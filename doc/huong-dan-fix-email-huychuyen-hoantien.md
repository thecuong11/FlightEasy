# Hướng dẫn Fix — Xử lý hủy chuyến bay · Email Service · Logic hoàn tiền

> Tài liệu này hướng dẫn **cách sửa từng lỗi** đã liệt kê trong `GAP_ANALYSIS_EMAIL_HUYCHUYEN_HOANTIEN.md` — có code đầy đủ, theo đúng thứ tự nên làm để không bị phụ thuộc ngược.
> Stack: Spring Boot 3.5 · Thymeleaf · JavaMailSender · JPA.

---

## Tổng quan các bước

| Bước | Nội dung | Sửa lỗi số |
|------|----------|------------|
| 1 | Thêm query tìm booking theo chuyến bay | chuẩn bị cho bước 2 |
| 2 | Viết logic thật xử lý hủy chuyến bay trong `BookingService` | #1, #7 |
| 3 | Gộp listener, xóa trùng lặp, gọi logic thật | #1, #7 |
| 4 | Thêm email + template "chuyến bay bị hủy" | #1, #2 |
| 5 | Tạo 5 template HTML còn thiếu | #2 |
| 6 | Xác nhận & khóa cứng bean Thymeleaf bằng `@Qualifier` | #6 |
| 7 | Sửa `calculateRefund()` theo `isRefundable`/`refundFeePercent` | #4 |
| 8 | Hoàn tiền thật qua VNPay (refund API) | #3 |
| 9 | Sửa `retrySend()` không mất context | #5 |
| 10 | Test plan | tất cả |

---

## Bước 1 — Thêm query tìm booking theo chuyến bay

**File:** `src/main/java/com/flighteasy/repository/BookingRepository.java`

Hiện chưa có cách nào tra ra các booking đã `CONFIRMED` gắn với 1 `Flight` cụ thể. Thêm method sau (đặt cạnh `findConfirmedBookingsForCheckin`):

```java
@Query("""
    SELECT DISTINCT b FROM Booking b
    LEFT JOIN FETCH b.segments s
    LEFT JOIN FETCH s.passengers p
    LEFT JOIN FETCH p.seat
    LEFT JOIN FETCH s.flightClass fc
    LEFT JOIN FETCH fc.flight
    WHERE fc.flight.id = :flightId
    AND b.status = 'CONFIRMED'
""")
List<Booking> findConfirmedBookingsByFlightId(@Param("flightId") Long flightId);
```

> Dùng `LEFT JOIN FETCH` giống các query khác trong file để tránh `LazyInitializationException` khi listener (chạy async, ngoài transaction gốc) truy cập `segments`/`passengers`/`seat`.

---

## Bước 2 — Viết logic thật xử lý hủy chuyến bay

**File:** `src/main/java/com/flighteasy/service/BookingService.java`

Thêm 1 method public mới. Method này **tái sử dụng** `releaseSeatsForBooking()` đã có sẵn (đang là `private`, giữ nguyên private, gọi từ trong cùng class):

```java
@Transactional
public void cancelBookingsForCancelledFlight(Flight flight) {
    List<Booking> affected = bookingRepository.findConfirmedBookingsByFlightId(flight.getId());

    for (Booking booking : affected) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelReason("Chuyến bay " + flight.getFlightNumber() + " đã bị hãng hủy");
        booking.setRefundAmount(booking.getTotalPrice()); // hủy do lỗi hãng bay -> hoàn 100%, không áp dụng isRefundable/phí
        bookingRepository.save(booking);

        releaseSeatsForBooking(booking);
        eventPublisher.publishEvent(new BookingCancelledEvent(booking));
    }

    log.info("Flight {} cancelled: auto-cancelled {} confirmed bookings", flight.getFlightNumber(), affected.size());
}
```

Import cần thêm ở đầu file: `com.flighteasy.entity.Flight` (đã import `entity.*` sẵn nên không cần thêm gì).

**Vì sao hoàn 100% không qua `calculateRefund()`:** khi hãng bay hủy chuyến, lỗi không thuộc về khách hàng — hoàn toàn khác trường hợp khách tự hủy (áp dụng chính sách `isRefundable`/phí theo giờ). Nếu nghiệp vụ của bạn muốn áp `refundFeePercent` ngay cả trong trường hợp này, thay dòng `setRefundAmount` bằng cách gọi `calculateRefundForBooking(booking)` (xem Bước 7) — nhưng thông lệ ngành hàng không là hoàn 100% khi lỗi thuộc về hãng.

**Việc tái sử dụng `BookingCancelledEvent`:** vì listener `BookingEventListener.onBookingCancelled()` đã có sẵn logic gửi `sendBookingCancellation()` (dùng template `booking-cancelled`), publish lại đúng event này sẽ tự động gửi email hủy vé — nhưng nội dung email generic ("hủy vé") không nói rõ lý do là "hãng bay hủy chuyến". Vì vậy ở Bước 4 ta sẽ thêm 1 email **riêng** (`flight-cancelled`) rõ ràng hơn, gửi thêm/thay thế cho khách.

> Chọn 1 trong 2 cách — không làm cả hai để tránh gửi 2 email cho cùng 1 booking:
> - **Cách A (đơn giản hơn):** không publish `BookingCancelledEvent` trong method trên, chỉ gọi thẳng `emailService.sendFlightCancellation(...)` (Bước 4) cho từng booking.
> - **Cách B:** publish `BookingCancelledEvent` như trên và bỏ template `flight-cancelled`, chấp nhận nội dung email generic.
>
> Hướng dẫn dưới đây dùng **Cách A** vì rõ ràng hơn cho khách hàng.

Sửa lại method ở trên theo Cách A:

```java
@Transactional
public void cancelBookingsForCancelledFlight(Flight flight) {
    List<Booking> affected = bookingRepository.findConfirmedBookingsByFlightId(flight.getId());

    for (Booking booking : affected) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelReason("Chuyến bay " + flight.getFlightNumber() + " đã bị hãng hủy");
        booking.setRefundAmount(booking.getTotalPrice());
        bookingRepository.save(booking);

        releaseSeatsForBooking(booking);
    }

    log.info("Flight {} cancelled: auto-cancelled {} confirmed bookings", flight.getFlightNumber(), affected.size());

    // Publish 1 event riêng mang theo cả danh sách booking, để listener gửi email + (sau này) trigger refund thật
    eventPublisher.publishEvent(new FlightCancelledEvent(flight, affected));
}
```

Cần sửa `FlightCancelledEvent` để mang thêm danh sách booking bị ảnh hưởng — xem Bước 3.

---

## Bước 3 — Gộp listener, xóa trùng lặp, gọi logic thật

### 3.1 Cập nhật `FlightCancelledEvent` để mang theo danh sách booking

**File:** `src/main/java/com/flighteasy/event/FlightCancelledEvent.java`

```java
package com.flighteasy.event;

import com.flighteasy.entity.Booking;
import com.flighteasy.entity.Flight;

import java.util.List;

public class FlightCancelledEvent {
    private final Flight flight;
    private final List<Booking> affectedBookings;

    public FlightCancelledEvent(Flight flight, List<Booking> affectedBookings) {
        this.flight = flight;
        this.affectedBookings = affectedBookings;
    }

    public Flight getFlight() {
        return flight;
    }

    public List<Booking> getAffectedBookings() {
        return affectedBookings;
    }
}
```

### 3.2 Xóa listener trùng, chỉ giữ đúng 1 nơi xử lý

Vì `FlightService.updateFlightStatus()` hiện publish `FlightCancelledEvent` **trực tiếp** (không qua `BookingService`), cần đổi nguồn publish sang `BookingService.cancelBookingsForCancelledFlight()` ở Bước 2 (nó đã tự publish event mới có kèm `affectedBookings`).

**File:** `src/main/java/com/flighteasy/service/FlightService.java` (dòng 141-143)

Trước:
```java
if (request.status() == FlightStatus.CANCELLED){
    eventPublisher.publishEvent(new FlightCancelledEvent(flight));
}
```

Sau — inject thêm `BookingService` vào `FlightService` và gọi:
```java
if (request.status() == FlightStatus.CANCELLED){
    bookingService.cancelBookingsForCancelledFlight(flight);
}
```

Thêm field:
```java
private final BookingService bookingService;
```

> `FlightService` không còn tự publish `FlightCancelledEvent` nữa — việc publish đã chuyển vào `BookingService.cancelBookingsForCancelledFlight()` (Bước 2), nơi có sẵn danh sách `affectedBookings` để đính kèm.

### 3.3 Xóa method thừa trong `BookingEventListener`, chỉ giữ `FlightCancelledListener`

**File:** `src/main/java/com/flighteasy/event/BookingEventListener.java` — xóa hẳn method `onFlightCancelled` (dòng 43-47) vì đã có `FlightCancelledListener` xử lý riêng, tránh 2 nơi cùng lắng nghe 1 event:

```java
// XÓA đoạn này khỏi BookingEventListener.java:
@EventListener
@Async("emailTaskExecutor")
public void onFlightCancelled(FlightCancelledEvent event) {
    log.info("Flight {} cancelled - sending notifications", event.getFlight().getFlightNumber());
}
```

### 3.4 Viết logic thật cho `FlightCancelledListener`

**File:** `src/main/java/com/flighteasy/event/FlightCancelledListener.java`

```java
package com.flighteasy.event;

import com.flighteasy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightCancelledListener {

    private final EmailService emailService;

    @EventListener
    public void onFlightCancelled(FlightCancelledEvent event) {
        log.info("Flight {} cancelled - notifying {} affected bookings",
                event.getFlight().getFlightNumber(), event.getAffectedBookings().size());

        emailService.sendFlightCancellationNotification(event.getFlight(), event.getAffectedBookings());
    }
}
```

> Không cần `@Async` ở đây vì `EmailService.sendFlightCancellationNotification()` (Bước 4) tự đã có `@Async("emailTaskExecutor")` trên method — tránh lồng 2 lớp async không cần thiết.

---

## Bước 4 — Thêm email + template "chuyến bay bị hủy"

### 4.1 Method mới trong `EmailService`

**File:** `src/main/java/com/flighteasy/service/EmailService.java` — thêm cạnh `sendFlightDelayNotification`:

```java
@Async("emailTaskExecutor")
public void sendFlightCancellationNotification(Flight flight, List<Booking> affectedBookings) {
    affectedBookings.forEach(booking -> {
        Context context = new Context();
        context.setVariable("pnrCode", booking.getPnrCode());
        context.setVariable("flightNumber", flight.getFlightNumber());
        context.setVariable("from", getOriginName(booking));
        context.setVariable("to", getDestinationName(booking));
        context.setVariable("departureTime", getDepartureTime(booking));
        context.setVariable("refundAmount", formatCurrency(booking.getRefundAmount()));

        sendEmail(
                booking.getContactEmail(),
                "🛑 Chuyến bay " + flight.getFlightNumber() + " đã bị hủy - " + booking.getPnrCode(),
                "flight-cancelled",
                context,
                booking.getPnrCode()
        );
    });
}
```

### 4.2 Template `flight-cancelled.html`

Tạo file mới: `src/main/resources/templates/emails/flight-cancelled.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

    <div style="background:#b91c1c; padding:24px; text-align:center">
        <h1 style="color:white; margin:0; font-size:24px">🛑 FlightEasy</h1>
        <p style="color:#fecaca; margin:8px 0 0">Chuyến bay của bạn đã bị hủy</p>
    </div>

    <div style="padding:24px; text-align:center; background:#fef2f2; border-bottom:1px solid #fecaca">
        <p style="margin:0; color:#6b7280; font-size:14px">Mã đặt chỗ</p>
        <h2 style="font-size:32px; letter-spacing:6px; color:#b91c1c; margin:8px 0" th:text="${pnrCode}">AB3X9K</h2>
    </div>

    <div style="padding:24px">
        <h3 style="color:#111827; margin:0 0 16px">Thông tin chuyến bay bị hủy</h3>
        <table style="width:100%; border-collapse:collapse">
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280; width:40%">Chuyến bay</td>
                <td style="padding:10px 0; font-weight:bold" th:text="${flightNumber}">VJ123</td>
            </tr>
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280">Hành trình</td>
                <td style="padding:10px 0; font-weight:bold">
                    <span th:text="${from}">Hồ Chí Minh</span> → <span th:text="${to}">Hà Nội</span>
                </td>
            </tr>
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280">Giờ khởi hành (dự kiến cũ)</td>
                <td style="padding:10px 0; font-weight:bold"
                    th:text="${#temporals.format(departureTime, 'HH:mm - dd/MM/yyyy')}">06:00 - 15/06/2025</td>
            </tr>
            <tr>
                <td style="padding:10px 0; color:#6b7280">Số tiền hoàn</td>
                <td style="padding:10px 0; font-weight:bold; color:#059669" th:text="${refundAmount}">1,555,000 ₫</td>
            </tr>
        </table>
    </div>

    <div style="padding:0 24px 24px; color:#374151; font-size:14px; line-height:1.6">
        <p>Chúng tôi rất tiếc phải thông báo chuyến bay của bạn đã bị hãng hàng không hủy. Toàn bộ số tiền sẽ được hoàn lại vào phương thức thanh toán ban đầu trong vòng 5-7 ngày làm việc.</p>
    </div>

    <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
        <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
        <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
    </div>

</div>
</body>
</html>
```

---

## Bước 5 — Tạo 5 template HTML còn thiếu

Thư mục `src/main/resources/templates/emails/` hiện chỉ có `booking-confirmed.html`. Tạo thêm các file sau (biến `th:text` phải khớp đúng tên `context.setVariable(...)` trong `EmailService.java` — đã đối chiếu lại code hiện tại).

### 5.1 `booking-cancelled.html`

> Dùng cho `sendBookingCancellation()` — biến: `pnrCode`, `refundAmount`, `cancelReason`.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

    <div style="background:#374151; padding:24px; text-align:center">
        <h1 style="color:white; margin:0; font-size:24px">❌ FlightEasy</h1>
        <p style="color:#d1d5db; margin:8px 0 0">Xác nhận hủy vé</p>
    </div>

    <div style="padding:24px; text-align:center; background:#f9fafb; border-bottom:1px solid #f3f4f6">
        <p style="margin:0; color:#6b7280; font-size:14px">Mã đặt chỗ</p>
        <h2 style="font-size:32px; letter-spacing:6px; color:#374151; margin:8px 0" th:text="${pnrCode}">AB3X9K</h2>
    </div>

    <div style="padding:24px">
        <table style="width:100%; border-collapse:collapse">
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280; width:45%">Lý do hủy</td>
                <td style="padding:10px 0; font-weight:bold" th:text="${cancelReason} ?: 'Khách hàng yêu cầu hủy'">Khách hàng yêu cầu hủy</td>
            </tr>
            <tr>
                <td style="padding:10px 0; color:#6b7280">Số tiền hoàn</td>
                <td style="padding:10px 0; font-weight:bold; color:#059669" th:text="${refundAmount}">700,000 ₫</td>
            </tr>
        </table>
    </div>

    <div style="padding:0 24px 24px; color:#374151; font-size:14px; line-height:1.6">
        <p>Booking của bạn đã được hủy thành công. Nếu có số tiền cần hoàn, chúng tôi sẽ chuyển vào phương thức thanh toán ban đầu trong vòng 5-7 ngày làm việc.</p>
    </div>

    <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
        <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
        <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
    </div>

</div>
</body>
</html>
```

### 5.2 `flight-delayed.html`

> Dùng cho `sendFlightDelayNotification()` — biến: `pnrCode`, `flightNumber`, `newDeparture`, `delayMinutes`.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

    <div style="background:#b45309; padding:24px; text-align:center">
        <h1 style="color:white; margin:0; font-size:24px">⚠️ FlightEasy</h1>
        <p style="color:#fde68a; margin:8px 0 0">Chuyến bay của bạn bị trễ</p>
    </div>

    <div style="padding:24px">
        <table style="width:100%; border-collapse:collapse">
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280; width:45%">Mã đặt chỗ</td>
                <td style="padding:10px 0; font-weight:bold" th:text="${pnrCode}">AB3X9K</td>
            </tr>
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280">Chuyến bay</td>
                <td style="padding:10px 0; font-weight:bold" th:text="${flightNumber}">VJ123</td>
            </tr>
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280">Giờ khởi hành mới</td>
                <td style="padding:10px 0; font-weight:bold"
                    th:text="${#temporals.format(newDeparture, 'HH:mm - dd/MM/yyyy')}">08:30 - 15/06/2025</td>
            </tr>
            <tr>
                <td style="padding:10px 0; color:#6b7280">Thời gian trễ</td>
                <td style="padding:10px 0; font-weight:bold; color:#b45309">
                    <span th:text="${delayMinutes}">150</span> phút
                </td>
            </tr>
        </table>
    </div>

    <div style="padding:0 24px 24px; color:#374151; font-size:14px; line-height:1.6">
        <p>Chúng tôi thành thật xin lỗi vì sự bất tiện này. Vui lòng đến sân bay đúng theo giờ khởi hành mới ở trên.</p>
    </div>

    <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
        <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
        <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
    </div>

</div>
</body>
</html>
```

### 5.3 `checkin-reminder.html`

> Dùng cho `sendCheckinReminder()` — biến: `pnrCode`, `flightNumber`, `departureTime`.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

    <div style="background:#1a56db; padding:24px; text-align:center">
        <h1 style="color:white; margin:0; font-size:24px">🔔 FlightEasy</h1>
        <p style="color:#bfdbfe; margin:8px 0 0">Đừng quên check-in chuyến bay!</p>
    </div>

    <div style="padding:24px">
        <table style="width:100%; border-collapse:collapse">
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280; width:45%">Mã đặt chỗ</td>
                <td style="padding:10px 0; font-weight:bold" th:text="${pnrCode}">AB3X9K</td>
            </tr>
            <tr style="border-bottom:1px solid #f3f4f6">
                <td style="padding:10px 0; color:#6b7280">Chuyến bay</td>
                <td style="padding:10px 0; font-weight:bold" th:text="${flightNumber}">VJ123</td>
            </tr>
            <tr>
                <td style="padding:10px 0; color:#6b7280">Giờ khởi hành</td>
                <td style="padding:10px 0; font-weight:bold"
                    th:text="${#temporals.format(departureTime, 'HH:mm - dd/MM/yyyy')}">06:00 - 15/06/2025</td>
            </tr>
        </table>
    </div>

    <div style="padding:0 24px 24px; color:#374151; font-size:14px; line-height:1.6">
        <p>Chuyến bay của bạn sắp khởi hành. Hãy check-in online để tiết kiệm thời gian tại sân bay.</p>
    </div>

    <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
        <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
        <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
    </div>

</div>
</body>
</html>
```

### 5.4 `password-reset.html`

> Dùng cho `sendPasswordResetEmail()` — biến: `resetLink`, `expiryMinutes`.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

    <div style="background:#1a56db; padding:24px; text-align:center">
        <h1 style="color:white; margin:0; font-size:24px">🔑 FlightEasy</h1>
        <p style="color:#bfdbfe; margin:8px 0 0">Yêu cầu đặt lại mật khẩu</p>
    </div>

    <div style="padding:24px; color:#374151; font-size:14px; line-height:1.6">
        <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Nhấn nút bên dưới để đặt mật khẩu mới:</p>

        <div style="text-align:center; margin:24px 0">
            <a th:href="${resetLink}" href="#"
               style="background:#1a56db; color:white; padding:12px 32px; border-radius:6px; text-decoration:none; font-weight:bold; display:inline-block">
                Đặt lại mật khẩu
            </a>
        </div>

        <p>Liên kết này sẽ hết hạn sau <span th:text="${expiryMinutes}" style="font-weight:bold">60</span> phút.</p>
        <p style="color:#9ca3af; font-size:12px">Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
    </div>

    <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
        <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
        <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
    </div>

</div>
</body>
</html>
```

### 5.5 `welcome.html`

> Dùng cho `sendWelcomeEmail()` — biến: `fullName`.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

    <div style="background:#1a56db; padding:32px; text-align:center">
        <h1 style="color:white; margin:0; font-size:24px">👋 Chào mừng đến FlightEasy!</h1>
    </div>

    <div style="padding:24px; color:#374151; font-size:14px; line-height:1.6">
        <p>Xin chào <span th:text="${fullName}" style="font-weight:bold">Nguyễn Văn A</span>,</p>
        <p>Cảm ơn bạn đã đăng ký tài khoản tại FlightEasy. Giờ đây bạn có thể tìm kiếm và đặt vé máy bay tới hàng trăm điểm đến trong nước và quốc tế.</p>
    </div>

    <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
        <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
        <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
    </div>

</div>
</body>
</html>
```

---

## Bước 6 — Khóa cứng bean Thymeleaf bằng `@Qualifier`

**File:** `src/main/java/com/flighteasy/service/EmailService.java`

Vấn đề: có 2 bean `SpringTemplateEngine` trong context (`templateEngine` mặc định của Spring Boot autoconfig, và `emailTemplateEngine` custom trong `ThymeleafEmailConfig`). Vì field tên trùng `templateEngine`, Spring có thể match nhầm sang bean mặc định. Sửa bằng cách chỉ định rõ:

```java
import org.springframework.beans.factory.annotation.Qualifier;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender,
                         @Qualifier("emailTemplateEngine") SpringTemplateEngine templateEngine,
                         EmailLogRepository emailLogRepository) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.emailLogRepository = emailLogRepository;
    }
    // ...
}
```

> Vì thêm constructor tường minh, phải **xóa** `@RequiredArgsConstructor` trên class (Lombok sẽ conflict nếu giữ cả hai — 2 constructor cùng tồn tại).

Sau khi sửa, chạy thử gửi 1 email `booking-confirmed` thật (hoặc dùng MailHog theo Bước 10.1 của `huong-dan-06-email-notification.md`) và xem log — nếu trước đó bị lỗi `TemplateInputException: Error resolving template` thì sau khi thêm `@Qualifier` sẽ hết.

---

## Bước 7 — Sửa `calculateRefund()` theo `isRefundable`/`refundFeePercent`

**File:** `src/main/java/com/flighteasy/service/BookingService.java` (dòng 190-201)

Trước:
```java
private BigDecimal calculateRefund(Booking booking) {
    LocalDateTime departureTime = booking.getSegments().stream().findFirst()
            .orElseThrow(() -> new NotFoundException("Booking không có segment"))
            .getFlightClass().getFlight().getDepartureTime();

    long hoursUntilDeparture = Duration.between(LocalDateTime.now(), departureTime).toHours();

    if (hoursUntilDeparture >= 24) {
        return booking.getTotalPrice().multiply(BigDecimal.valueOf(0.70));
    }
    return BigDecimal.ZERO;
}
```

Sau:
```java
private BigDecimal calculateRefund(Booking booking) {
    FlightClass flightClass = booking.getSegments().stream().findFirst()
            .orElseThrow(() -> new NotFoundException("Booking không có segment"))
            .getFlightClass();

    // Vé không hoàn tiền (vd giá khuyến mãi) -> không hoàn bất kể thời điểm hủy
    if (Boolean.FALSE.equals(flightClass.getIsRefundable())) {
        return BigDecimal.ZERO;
    }

    LocalDateTime departureTime = flightClass.getFlight().getDepartureTime();
    long hoursUntilDeparture = Duration.between(LocalDateTime.now(), departureTime).toHours();

    // Hủy trong vòng 24h trước giờ bay -> không hoàn, kể cả vé refundable
    if (hoursUntilDeparture < 24) {
        return BigDecimal.ZERO;
    }

    int feePercent = flightClass.getRefundFeePercent() != null ? flightClass.getRefundFeePercent() : 0;
    BigDecimal refundRate = BigDecimal.ONE.subtract(
            BigDecimal.valueOf(feePercent).divide(BigDecimal.valueOf(100)));

    return booking.getTotalPrice().multiply(refundRate);
}
```

> Rule cũ (70% cố định khi >=24h) tương đương `refundFeePercent = 30` cho mọi hạng vé — nếu muốn giữ hành vi cũ làm mặc định, đặt `refundFeePercent = 30` khi tạo `FlightClass` (thay vì mặc định `0` như hiện tại trong `FlightClass.java:37`). Cần thống nhất với nghiệp vụ trước khi đổi default.

---

## Bước 8 — Hoàn tiền thật qua VNPay

> Đây là phần **tích hợp lớn nhất**, cần API key/secret thật của VNPay merchant và phải test trên môi trường sandbox trước. Dưới đây là khung sườn (skeleton) đúng chuẩn API `vnp_Command=refund` của VNPay — bạn cần điền `vnp_TmnCode`/`hashSecret` đã có sẵn trong `VNPayService` hiện tại và test với sandbox.

### 8.1 Thêm method refund vào `VNPayService`

```java
public boolean refundTransaction(Payment payment, BigDecimal refundAmount, String user) {
    try {
        String vnpRequestId = UUID.randomUUID().toString().substring(0, 8);
        String vnpCreateDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String vnpTransactionType = "02"; // 02 = hoàn toàn phần, 03 = hoàn một phần

        Map<String, String> params = new HashMap<>();
        params.put("vnp_RequestId", vnpRequestId);
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "refund");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_TransactionType", vnpTransactionType);
        params.put("vnp_TxnRef", payment.getVnpTxnRef());
        params.put("vnp_Amount", String.valueOf(refundAmount.multiply(BigDecimal.valueOf(100)).longValue()));
        params.put("vnp_TransactionNo", payment.getVnpTransactionNo());
        params.put("vnp_TransactionDate", payment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_CreateBy", user);
        params.put("vnp_CreateDate", vnpCreateDate);
        params.put("vnp_IpAddr", "127.0.0.1");

        String hashData = String.join("|",
                vnpRequestId, params.get("vnp_Version"), params.get("vnp_Command"), tmnCode,
                vnpTransactionType, payment.getVnpTxnRef(), params.get("vnp_Amount"),
                payment.getVnpTransactionNo(), params.get("vnp_TransactionDate"), user, vnpCreateDate, "127.0.0.1");
        params.put("vnp_SecureHash", hmacSha512(hashSecret, hashData));

        Map<String, Object> response = vnpayRestTemplate.postForObject(vnpApiUrl, params, Map.class);

        String responseCode = String.valueOf(response.get("vnp_ResponseCode"));
        if ("00".equals(responseCode)) {
            payment.setRefundAmount(refundAmount);
            payment.setRefundTransId(String.valueOf(response.get("vnp_TransactionNo")));
            payment.setRefundedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return true;
        }

        log.error("VNPay refund failed for txnRef={}, responseCode={}", payment.getVnpTxnRef(), responseCode);
        return false;
    } catch (Exception e) {
        log.error("VNPay refund error for txnRef={}: {}", payment.getVnpTxnRef(), e.getMessage());
        return false;
    }
}
```

> Cần inject thêm `PaymentRepository` và `RestTemplate` (dùng `vnpayRestTemplate` bean đã đề xuất ở `BE_CODE_REVIEW.md` mục M-4, tránh `new RestTemplate()` mỗi lần gọi). Endpoint sandbox VNPay: `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`.

### 8.2 Gọi refund thật ở `BookingService.cancelBooking()` / `cancelBookingsForCancelledFlight()`

Thêm `PaymentRepository` và `VNPayService` vào `BookingService`, sau khi tính `refundAmount` và trước khi publish event:

```java
if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
    paymentRepository.findByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCESS)
            .ifPresent(payment -> vnPayService.refundTransaction(payment, refundAmount, "SYSTEM"));
}
```

> Cần thêm method `findByBookingIdAndStatus` vào `PaymentRepository` nếu chưa có. Nên bọc lời gọi này trong xử lý lỗi riêng (không để refund thất bại làm rollback toàn bộ việc hủy booking) — ví dụ tách ra 1 event/`@Async` riêng hoặc dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` để refund chạy sau khi transaction hủy booking đã commit thành công.

---

## Bước 9 — Sửa `retrySend()` không mất context

**File:** `src/main/java/com/flighteasy/entity/EmailLog.java` — thêm cột lưu snapshot dữ liệu:

```java
@Column(columnDefinition = "text")
private String contextJson;
```

**File:** `src/main/java/com/flighteasy/service/EmailService.java` — dùng `ObjectMapper` (Jackson, đã có sẵn trong Spring Boot) để lưu/khôi phục `Context`:

```java
private final ObjectMapper objectMapper; // inject thêm qua constructor cùng lúc với Bước 6

public void sendEmail(String to, String subject, String templateName, Context context, String referenceId) {
    String contextJson;
    try {
        contextJson = objectMapper.writeValueAsString(context.getVariableNames().stream()
                .collect(Collectors.toMap(name -> name, context::getVariable)));
    } catch (Exception e) {
        contextJson = "{}";
    }

    EmailLog emailLog = EmailLog.builder()
            .recipient(to)
            .subject(subject)
            .templateName(templateName)
            .referenceId(referenceId)
            .status("PENDING")
            .attempts(0)
            .contextJson(contextJson)
            .build();
    emailLog = emailLogRepository.save(emailLog);

    doSend(emailLog, context, templateName);
}

public void retrySend(EmailLog emailLog) {
    Context context = new Context();
    try {
        Map<String, Object> variables = objectMapper.readValue(
                emailLog.getContextJson() != null ? emailLog.getContextJson() : "{}",
                new TypeReference<Map<String, Object>>() {});
        variables.forEach(context::setVariable);
    } catch (Exception e) {
        log.error("Failed to restore context for email {}: {}", emailLog.getId(), e.getMessage());
    }
    doSend(emailLog, context, emailLog.getTemplateName());
}
```

> Lưu ý: `LocalDateTime`/`BigDecimal` cần Jackson module `JavaTimeModule` để serialize đúng (Spring Boot tự đăng ký sẵn `ObjectMapper` bean có module này nếu dùng `spring-boot-starter-json`, không cần cấu hình thêm).

---

## Bước 10 — Test plan

| # | Hành động | Kết quả mong đợi |
|---|-----------|-------------------|
| 1 | Admin đổi trạng thái 1 chuyến bay đang có booking `CONFIRMED` sang `CANCELLED` | Tất cả booking `CONFIRMED` của chuyến đó chuyển `CANCELLED`, `refundAmount = totalPrice`, ghế được release |
| 2 | Sau bước 1 | Mỗi khách nhận đúng 1 email "🛑 Chuyến bay ... đã bị hủy" (không nhận thêm email "hủy vé" trùng lặp) |
| 3 | Gửi thử `sendBookingCancellation`, `sendFlightDelayNotification`, `sendCheckinReminder`, `sendPasswordResetEmail`, `sendWelcomeEmail` | Không còn `TemplateInputException` trong log, email hiển thị đúng nội dung (dùng MailHog xem trực tiếp) |
| 4 | Khách hủy vé hạng "Economy khuyến mãi" (`isRefundable=false`) trước giờ bay > 24h | `refundAmount = 0`, không phải 70% như trước |
| 5 | Khách hủy vé hạng "Business" (`isRefundable=true`, `refundFeePercent=10`) trước giờ bay > 24h | `refundAmount = 90% totalPrice` |
| 6 | Tắt SMTP server, trigger 1 email fail → bật lại SMTP, đợi `EmailRetryScheduler` chạy | Email retry gửi thành công **với đầy đủ nội dung** (pnrCode, số tiền...) thay vì rỗng |
| 7 | (Cần sandbox VNPay) Hủy 1 booking đã thanh toán thành công | `payments.refund_trans_id`/`refunded_at` được set, gọi API VNPay trả `vnp_ResponseCode = 00` |

---

## Tóm tắt thay đổi theo file

| File | Thay đổi |
|------|----------|
| `repository/BookingRepository.java` | + `findConfirmedBookingsByFlightId` |
| `service/BookingService.java` | + `cancelBookingsForCancelledFlight`, sửa `calculateRefund`, + gọi VNPay refund |
| `service/FlightService.java` | Đổi publish event trực tiếp → gọi `bookingService.cancelBookingsForCancelledFlight` |
| `event/FlightCancelledEvent.java` | + field `affectedBookings` |
| `event/FlightCancelledListener.java` | Viết logic thật, gọi `EmailService` |
| `event/BookingEventListener.java` | Xóa method `onFlightCancelled` trùng lặp |
| `service/EmailService.java` | + `sendFlightCancellationNotification`, + `@Qualifier`, sửa `sendEmail`/`retrySend` để lưu/khôi phục context |
| `entity/EmailLog.java` | + cột `contextJson` |
| `service/VNPayService.java` | + `refundTransaction(...)` |
| `resources/templates/emails/*.html` | + 6 file mới: `flight-cancelled`, `booking-cancelled`, `flight-delayed`, `checkin-reminder`, `password-reset`, `welcome` |