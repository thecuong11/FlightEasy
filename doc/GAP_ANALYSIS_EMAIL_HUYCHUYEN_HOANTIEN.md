# Phân tích lỗ hổng logic: Email Service · Xử lý hủy chuyến bay · Hoàn tiền

> Phạm vi: `EmailService`, `FlightCancelledListener`/`BookingEventListener`, logic `refundAmount` trong `BookingService`/`VNPayService`.
> Ngày phân tích: 2026-07-06

---

## Tóm tắt

Ba khu vực người dùng nêu đều xác nhận là **có vấn đề thật**, và khi đào sâu còn phát hiện thêm các gap liên đới (đặc biệt là **email template HTML bị thiếu 5/6 file** và **hoàn tiền không có nghiệp vụ gọi cổng thanh toán thật**). Danh sách chi tiết bên dưới, xếp theo mức độ ảnh hưởng.

| # | Vấn đề | File | Mức độ |
|---|--------|------|--------|
| 1 | `FlightCancelledListener`/`BookingEventListener.onFlightCancelled` chỉ log, không xử lý gì | `event/FlightCancelledListener.java`, `event/BookingEventListener.java:43-47` | 🔴 Critical |
| 2 | 5/6 template email không tồn tại file → mọi email trừ "xác nhận đặt vé" đều fail âm thầm | `resources/templates/emails/` | 🔴 Critical |
| 3 | Không có nghiệp vụ hoàn tiền thật qua VNPay | `service/VNPayService.java` | 🔴 Critical |
| 4 | `calculateRefund()` bỏ qua `isRefundable`/`refundFeePercent` của hạng vé | `service/BookingService.java:190-201` | 🟠 High |
| 5 | `EmailService.retrySend()` mất toàn bộ dữ liệu context khi gửi lại | `service/EmailService.java:143-146` | 🟠 High |
| 6 | Nghi vấn inject nhầm bean `SpringTemplateEngine` mặc định của Spring Boot thay vì bean email riêng | `service/EmailService.java:30`, `config/ThymeleafEmailConfig.java` | 🟠 High (cần verify runtime) |
| 7 | Hai listener trùng nhau cho cùng 1 event | `event/FlightCancelledListener.java` + `event/BookingEventListener.java` | 🟡 Medium |

---

## 1 · `FlightCancelledListener` — chỉ log, chưa xử lý booking bị ảnh hưởng

**Hiện trạng:**

```java
// event/FlightCancelledListener.java
@EventListener
public void onFlightCancelled(FlightCancelledEvent event){
    log.info("Flight {} cancelled - processing affeccted booking...",
            event.getFlight().getFlightNumber());
}
```

```java
// event/BookingEventListener.java:43-47
@EventListener
@Async("emailTaskExecutor")
public void onFlightCancelled(FlightCancelledEvent event) {
    log.info("Flight {} cancelled - sending notifications", event.getFlight().getFlightNumber());
}
```

Event được publish tại `FlightService.updateFlightStatus()` (dòng 141-143) khi admin chuyển trạng thái chuyến bay sang `CANCELLED`. Nhưng **cả hai listener đều là no-op** — Spring vẫn gọi cả hai (event listener không loại trừ nhau), nghĩa là hiện có 2 chỗ code trùng lặp cùng "giả vờ" xử lý.

**Những gì đang thiếu hoàn toàn:**

1. **Không tìm booking bị ảnh hưởng.** `FlightCancelledEvent` chỉ mang `Flight`, không mang danh sách booking. `BookingRepository` hiện không có bất kỳ query nào theo `flightId`/`flightClassId` (chỉ có `findConfirmedBookingsForCheckin` theo khoảng thời gian khởi hành) — cần thêm query mới, ví dụ:
   ```java
   @Query("""
       SELECT DISTINCT b FROM Booking b
       JOIN b.segments s JOIN s.flightClass fc
       WHERE fc.flight.id = :flightId AND b.status = 'CONFIRMED'
   """)
   List<Booking> findConfirmedBookingsByFlightId(@Param("flightId") Long flightId);
   ```
2. **Không tự động hủy các booking đó** (chuyển `BookingStatus.CANCELLED`, set `cancelledAt`, `cancelReason = "Chuyến bay bị hủy bởi hãng"`).
3. **Không tính/ghi `refundAmount`.** Về nghiệp vụ, khi hãng bay hủy chuyến, thông lệ là hoàn **100%** bất kể `isRefundable` của hạng vé (khác hẳn khi khách tự hủy) — logic này chưa tồn tại ở đâu cả.
4. **Không giải phóng ghế.** `releaseSeatsForBooking()` hiện là `private` trong `BookingService` — listener không gọi được, cần expose (qua 1 method public mới trong `BookingService`, ví dụ `cancelBookingsDueToFlightCancellation(List<Booking>)`) thay vì tự viết lại logic.
5. **Không gửi email nào cho hành khách bị ảnh hưởng** — xem mục 2, hiện `EmailService` không có template/method dành riêng cho "chuyến bay bị hủy" (khác với `sendBookingCancellation` vốn dùng cho case khách tự hủy).

**Đề xuất gộp lại một chỗ xử lý** (xóa 1 trong 2 listener, hoặc để `FlightCancelledListener` làm nơi duy nhất gọi vào 1 method mới `BookingService.cancelBookingsForCancelledFlight(Flight flight)` để tránh xử lý refund/email 2 lần khi cả hai đều được hiện thực hoá sau này).

---

## 2 · `EmailService`

### 2a. Template HTML thiếu 5/6 file — lỗi âm thầm cho gần như mọi loại email

`ThymeleafEmailConfig` trỏ resolver tới `templates/emails/` (suffix `.html`), nhưng thư mục này chỉ có:

```
src/main/resources/templates/emails/booking-confirmed.html
```

Trong khi `EmailService` gọi các `templateName` sau — **không có file tương ứng nào tồn tại**:

| Method | `templateName` gọi | File tồn tại? |
|---|---|---|
| `sendBookingConfirmation` | `booking-confirmed` | ✅ có |
| `sendBookingCancellation` | `booking-cancelled` | ❌ thiếu |
| `sendFlightDelayNotification` | `flight-delayed` | ❌ thiếu |
| `sendCheckinReminder` | `checkin-reminder` | ❌ thiếu |
| `sendPasswordResetEmail` | `password-reset` | ❌ thiếu |
| `sendWelcomeEmail` | `welcome` | ❌ thiếu |

**Hậu quả:** `doSend()` bọc toàn bộ trong `try/catch` (dòng 148-179) — khi `templateEngine.process()` ném `TemplateInputException` do không tìm thấy file, exception bị nuốt, chỉ `log.info` lỗi rồi lưu `EmailLog.status = PENDING` → retry 3 lần → cuối cùng `FAILED`. **Không có exception nào nổi lên tầng gọi**, nghĩa là:
- Đặt vé thành công nhưng hủy vé, nhắc check-in, quên mật khẩu, chào mừng thành viên mới — **tất cả im lặng không gửi được email**, chỉ có thể phát hiện qua việc soi bảng `email_log` (`status = FAILED`).

→ Cần tạo 5 file HTML còn thiếu tương ứng, hoặc nếu các luồng này chưa được ưu tiên, nên tạm thời không gọi `sendXxx()` cho tới khi có template, để tránh burn threadpool + DB write vô ích mỗi lần fail.

### 2b. Nghi vấn EmailService đang dùng nhầm `SpringTemplateEngine` mặc định (cần verify runtime)

```java
// config/ThymeleafEmailConfig.java
@Bean("emailTemplateEngine")
public SpringTemplateEngine emailTemplateEngine() { ... prefix "templates/emails/" ... }
```

```java
// service/EmailService.java
private final SpringTemplateEngine templateEngine;   // không có @Qualifier
```

Vì dự án có `spring-boot-starter-thymeleaf`, Spring Boot autoconfig (`ThymeleafAutoConfiguration`) **tự tạo sẵn 1 bean `SpringTemplateEngine` tên `templateEngine`** (prefix mặc định `classpath:/templates/`, không có `/emails/`). Vậy trong context có **2 bean cùng kiểu `SpringTemplateEngine`**: `templateEngine` (mặc định) và `emailTemplateEngine` (custom).

Khi có nhiều bean cùng type và không có `@Primary`/`@Qualifier`, Spring fallback sang so khớp **tên bean với tên tham số constructor**. Tham số trong `EmailService` tên là `templateEngine` — trùng khớp chính xác với bean mặc định của Boot, không phải `emailTemplateEngine`. Nhiều khả năng `EmailService` đang **inject nhầm bean gốc** (trỏ `classpath:/templates/`, không có subfolder `emails/`), khiến kể cả `booking-confirmed` cũng có thể không resolve được, tùy cấu hình `spring.thymeleaf.prefix` trong `application.yml`.

→ Cần thêm `@Qualifier("emailTemplateEngine")` vào field này để chắc chắn, thay vì dựa vào naming convention ngầm (rất dễ vỡ khi ai đó đổi tên biến). Đây là điểm **cần xác nhận bằng cách chạy thử** (gửi 1 email thật hoặc bật debug log Thymeleaf) vì Claude không chạy được ứng dụng trong phiên này.

### 2c. `retrySend()` mất toàn bộ dữ liệu khi gửi lại → email retry hiển thị sai/rỗng

```java
public void retrySend(EmailLog emailLog) {
    Context context = new Context();      // ← rỗng, không có pnrCode/refundAmount/...
    doSend(emailLog, context, emailLog.getTemplateName());
}
```

`EmailLog` chỉ lưu `recipient`, `subject`, `templateName`, `referenceId` — **không lưu lại các biến Thymeleaf** (`pnrCode`, `refundAmount`, `flightNumber`, `newDeparture`…) đã set ở lần gửi đầu. `EmailRetryScheduler` chạy mỗi phút gọi `retrySend()` cho các email `PENDING` — khi retry thành công, email gửi ra sẽ **hiển thị rỗng/null** ở tất cả chỗ dùng biến (vd "Mã PNR:", "Số tiền hoàn: 0 đ" sai lệch thông tin thật).

→ Cần bổ sung cột (vd `payload_json` hoặc `TEXT`) trong `EmailLog` để lưu snapshot dữ liệu (hoặc lưu luôn HTML đã render sẵn ở lần gửi đầu, retry chỉ cần gửi lại HTML đó — không cần render lại).

---

## 3 · Logic `refundAmount`

### 3a. `calculateRefund()` bỏ qua chính sách hoàn vé theo hạng vé đã khai báo

```java
// BookingService.java:190-201
private BigDecimal calculateRefund(Booking booking) {
    LocalDateTime departureTime = ...getDepartureTime();
    long hoursUntilDeparture = Duration.between(LocalDateTime.now(), departureTime).toHours();

    if (hoursUntilDeparture >= 24) {
        return booking.getTotalPrice().multiply(BigDecimal.valueOf(0.70)); // luôn 70% cứng
    }
    return BigDecimal.ZERO;
}
```

`FlightClass` có sẵn 2 field khai báo chính sách hoàn vé lúc tạo chuyến bay (`FlightService.createFlight`, dòng 114-115):

```java
private Boolean isRefundable = true;
private Integer refundFeePercent = 0;
```

Nhưng `calculateRefund()` **không đọc 2 field này** — mọi hạng vé, kể cả vé "không hoàn" (`isRefundable = false`), vẫn được hoàn 70% nếu hủy trước 24h. Đây là sai lệch nghiệp vụ: khách mua vé khuyến mãi non-refundable vẫn được hoàn tiền như vé thường.

**Đề xuất sửa** (ví dụ, cần chốt lại rule thật với nghiệp vụ):
```java
private BigDecimal calculateRefund(Booking booking) {
    BookingSegment segment = booking.getSegments().stream().findFirst()
            .orElseThrow(() -> new NotFoundException("Booking không có segment"));
    FlightClass fc = segment.getFlightClass();

    if (Boolean.FALSE.equals(fc.getIsRefundable())) {
        return BigDecimal.ZERO;
    }

    long hoursUntilDeparture = Duration.between(LocalDateTime.now(), fc.getFlight().getDepartureTime()).toHours();
    if (hoursUntilDeparture < 24) {
        return BigDecimal.ZERO;
    }

    BigDecimal feePercent = BigDecimal.valueOf(fc.getRefundFeePercent() == null ? 0 : fc.getRefundFeePercent());
    BigDecimal refundRate = BigDecimal.ONE.subtract(feePercent.divide(BigDecimal.valueOf(100)));
    return booking.getTotalPrice().multiply(refundRate);
}
```

### 3b. Không có nghiệp vụ hoàn tiền thật qua cổng thanh toán — `refundAmount` chỉ là con số hiển thị

`Payment` entity đã có sẵn các cột phục vụ hoàn tiền:

```java
// entity/Payment.java:55-61
private BigDecimal refundAmount;
private String refundTransId;
private LocalDateTime refundedAt;
@ManyToOne @JoinColumn(name = "refunded_by")
private User refundedBy;
```

Nhưng **toàn bộ `VNPayService.java` không có bất kỳ dòng nào liên quan đến "refund"** — không gọi API `vnp_Command=refund` của VNPay, không set 4 field trên bao giờ. Nghĩa là:

- `booking.refundAmount` (tính ở mục 3a) chỉ là **con số hiển thị** trong email và response API (`CancelBookingResponse`).
- **Không có tiền nào thực sự được hoàn về thẻ/tài khoản khách hàng.** Nhân viên vận hành phải tự tra cứu và hoàn tiền thủ công ngoài hệ thống — nếu đây không phải chủ đích thiết kế (một số hệ thống cố tình để refund thủ công qua admin), thì đây là **gap nghiêm trọng nhất về tiền bạc** trong toàn bộ luồng.

→ Cần bổ sung: gọi API refund của VNPay (hoặc tạo quy trình admin duyệt hoàn tiền thủ công có ghi nhận `refundedBy`/`refundedAt`/`refundTransId`), và trigger nó tại đúng điểm `cancelBooking()`/`cancelBookingByAdmin()` sau khi tính xong `refundAmount`.

### 3c. `cancelBookingByAdmin()` hoàn 100% không qua `calculateRefund()`, không xét `isRefundable`

```java
// BookingService.java:298-314 (đã có refund/event, khác với mô tả cũ trong BE_CODE_REVIEW.md — xem ghi chú cuối)
if (booking.getStatus() == BookingStatus.CONFIRMED) {
    booking.setRefundAmount(booking.getTotalPrice()); // hoàn 100% khi admin hủy — cứng, không qua calculateRefund()
}
```

Nếu chủ đích nghiệp vụ là "admin hủy luôn hoàn 100%" (hợp lý vì lỗi không phải do khách) thì đoạn này ổn, nhưng nó **không đi qua đường hoàn tiền thật ở mục 3b** — nên vẫn dính chung gap đó.

---

## 4 · Hai listener trùng nhau cho cùng một event (dọn dẹp kỹ thuật)

`FlightCancelledListener` (class riêng) và `BookingEventListener.onFlightCancelled()` cùng lắng nghe `FlightCancelledEvent`. Cả hai sẽ **đều được Spring gọi** khi event publish — hiện vô hại vì cả hai chỉ log, nhưng nếu sau này chỉ một bên được bổ sung logic thật (refund/email/hủy booking), rất dễ quên implement bên còn lại, hoặc tệ hơn là cả hai đều implement → **xử lý refund/gửi email 2 lần** cho cùng 1 booking. Nên gộp về đúng 1 nơi duy nhất trước khi viết logic thật.

---

## 5 · Đối chiếu với `doc/BE_CODE_REVIEW.md` (đã có từ 2026-06-28)

- Mục **H-3** của báo cáo cũ mô tả `cancelBookingByAdmin()` "không publish event, không tính refund, không gửi email" — nhưng code hiện tại (dòng 298-314) **đã có** `booking.setRefundAmount(...)` và `eventPublisher.publishEvent(new BookingCancelledEvent(booking))`. Tức là phần này **đã được sửa một phần** sau ngày review, tài liệu cũ đã lỗi thời ở điểm này — nên cập nhật lại H-3 trong `BE_CODE_REVIEW.md` hoặc đánh dấu "đã fix".
- Báo cáo cũ **chưa đề cập** tới: template email bị thiếu file, nghi vấn sai bean Thymeleaf, `retrySend()` mất context, `calculateRefund()` bỏ qua `isRefundable`, và việc thiếu nghiệp vụ hoàn tiền thật qua VNPay — đây đều là phát hiện mới từ lần rà soát này.

---

## Bảng việc cần làm (đề xuất thứ tự ưu tiên)

| # | Việc cần làm | Vì sao ưu tiên |
|---|---|---|
| 1 | Tạo 5 file template email còn thiếu (`booking-cancelled`, `flight-delayed`, `checkin-reminder`, `password-reset`, `welcome`) | Toàn bộ email trừ xác nhận đặt vé đang fail âm thầm |
| 2 | Xác nhận (chạy thử) `EmailService` có đang inject đúng bean `emailTemplateEngine` không; thêm `@Qualifier` nếu cần | Ảnh hưởng mọi email kể cả email đang "chạy được" |
| 3 | Viết logic thật cho `FlightCancelledListener` (tìm booking → hủy → tính refund 100% → giải phóng ghế → gửi email) và gộp bỏ listener trùng | Tính năng "hủy chuyến" hiện không hoạt động, ảnh hưởng khách hàng thật |
| 4 | Sửa `calculateRefund()` để đọc `isRefundable`/`refundFeePercent` theo hạng vé | Sai số tiền hoàn cho vé non-refundable |
| 5 | Bổ sung nghiệp vụ hoàn tiền thật (gọi VNPay refund API hoặc quy trình admin duyệt) và set `refundTransId`/`refundedAt`/`refundedBy` | `refundAmount` hiện chỉ là số hiển thị, không có tiền thật được hoàn |
| 6 | Lưu snapshot dữ liệu vào `EmailLog` để `retrySend()` không mất context | Email retry hiện hiển thị sai/rỗng thông tin |