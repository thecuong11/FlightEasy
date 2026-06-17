# Hướng dẫn Fix — Lỗi thanh toán VNPay (Double Charge & Mất đồng bộ IPN)

> **Áp dụng cho:** Module Payment (Service 05) — `flighteasy-backend`
> **Mức độ:** BẮT BUỘC fix trước khi lên production
> **Bối cảnh:** Phát hiện qua test thủ công — (1) gọi lại API tạo link thanh toán dẫn đến bị trừ tiền 2 lần, (2) IPN không tới được server (do ngrok đổi domain) khiến Payment/Booking bị kẹt ở PENDING dù tiền đã trừ thật.

---

## Tổng quan các thay đổi

| # | File | Loại thay đổi | Vấn đề được fix |
|---|------|---------------|------------------|
| 1 | `PaymentService.java` | Sửa method `createPaymentLink()` | Chặn tạo Payment mới khi đã có Payment PENDING — tránh double charge |
| 2 | `VNPayService.java` | Thêm field `apiUrl`, thêm method `queryTransactionStatus()`, `confirmFromReconciliation()` | Gọi API `querydr` để đối soát; xử lý IPN miss |
| 3 | `PaymentRepository.java` | Thêm query method | Tìm các Payment PENDING quá lâu để đối soát |
| 4 | `PaymentReconciliationScheduler.java` (file mới) | Tạo mới | Tự động quét và đối soát Payment bị kẹt |
| 5 | `BookingExpiryScheduler.java` | Sửa điều kiện query | Không hủy Booking khi đang có Payment chờ đối soát |
| 6 | `application.yml` | Đã có sẵn — chỉ cần xác nhận | Cấu hình `vnpay.api-url` dùng cho bước 2 |

---

## Bước 1 — Sửa `PaymentService.java` — Chặn double charge

**File:** `src/main/java/com/fighteasy/service/PaymentService.java`
(theo cấu trúc package trong `huong-dan-05-payment-vnpay.md`, có thể là `com.example.flighteasy.service` tùy project của bạn)

### Vấn đề

Method `createPaymentLink()` hiện tại **luôn tạo Payment record mới** mỗi lần được gọi, chỉ check trạng thái của `Booking` (phải là `PENDING`) mà không check đã có `Payment` PENDING nào tồn tại cho booking đó chưa. Khi người dùng bấm lại nút thanh toán / gọi lại API, hệ thống sinh ra 1 `vnpTxnRef` mới → 1 giao dịch VNPay thật mới → bị trừ tiền thêm lần nữa.

### Sửa lại toàn bộ method `createPaymentLink`

Tìm đoạn này trong `PaymentService`:

```java
@Transactional
@Transactional
public CreatePaymentResponse createPaymentLink(CreatePaymentRequest request, String clientIp) {
    Booking booking = bookingRepository.findByPnrCode(request.pnrCode())
            .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));

    // BR-01: Chỉ cho phép PENDING booking
    if (booking.getStatus() != BookingStatus.PENDING) {
        throw new InvalidPaymentException("Booking không ở trạng thái chờ thanh toán");
    }

    // BR-02: Booking phải còn hạn
    if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
        throw new InvalidPaymentException("Booking đã hết hạn thanh toán");
    }

    // Tạo txnRef unique = PNR + timestamp
    String txnRef = request.pnrCode() + "-" + System.currentTimeMillis();
    long amountVnpay = booking.getTotalPrice()
            .multiply(BigDecimal.valueOf(100)).longValue();

    // Tạo Payment record (BR-03)
    Payment payment = Payment.builder()
            .booking(booking)
            .amount(booking.getTotalPrice())
            .amountVnpay(amountVnpay)
            .vnpTxnRef(txnRef)
            .status(PaymentStatus.PENDING)
            .build();
    payment = paymentRepository.save(payment);

    // Build VNPay URL
    String ip = clientIp != null ? clientIp : "127.0.0.1";
    String paymentUrl = vnPayService.createPaymentUrl(booking, payment, request.returnUrl(), ip);

    return new CreatePaymentResponse(paymentUrl, txnRef, booking.getTotalPrice(), booking.getExpiresAt());
}
```

Thay bằng:

```java
@Transactional
public CreatePaymentResponse createPaymentLink(CreatePaymentRequest request, String clientIp) {
    Booking booking = bookingRepository.findByPnrCode(request.pnrCode())
            .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));

    // BR-01: Chỉ cho phép PENDING booking
    if (booking.getStatus() != BookingStatus.PENDING) {
        throw new InvalidPaymentException("Booking không ở trạng thái chờ thanh toán");
    }

    // BR-02: Booking phải còn hạn
    if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
        throw new InvalidPaymentException("Booking đã hết hạn thanh toán");
    }

    String ip = clientIp != null ? clientIp : "127.0.0.1";

    // ===== FIX DOUBLE CHARGE =====
    // Nếu đã có Payment PENDING cho booking này (do user bấm lại / gọi lại API),
    // tái sử dụng cùng Payment record + txnRef, KHÔNG tạo giao dịch VNPay mới.
    Optional<Payment> existingPending = paymentRepository
            .findLatestByBookingId(booking.getId())
            .filter(p -> p.getStatus() == PaymentStatus.PENDING);

    if (existingPending.isPresent()) {
        Payment existing = existingPending.get();
        String paymentUrl = vnPayService.createPaymentUrl(booking, existing, request.returnUrl(), ip);
        return new CreatePaymentResponse(
            paymentUrl, existing.getVnpTxnRef(), booking.getTotalPrice(), booking.getExpiresAt()
        );
    }

    // Không có payment pending nào đang chờ -> tạo mới như cũ
    String txnRef = request.pnrCode() + "-" + System.currentTimeMillis();
    long amountVnpay = booking.getTotalPrice()
            .multiply(BigDecimal.valueOf(100)).longValue();

    Payment payment = Payment.builder()
            .booking(booking)
            .amount(booking.getTotalPrice())
            .amountVnpay(amountVnpay)
            .vnpTxnRef(txnRef)
            .status(PaymentStatus.PENDING)
            .build();
    payment = paymentRepository.save(payment);

    String paymentUrl = vnPayService.createPaymentUrl(booking, payment, request.returnUrl(), ip);

    return new CreatePaymentResponse(paymentUrl, txnRef, booking.getTotalPrice(), booking.getExpiresAt());
}
```

> Lưu ý nhỏ: bản gốc có `@Transactional` lặp 2 lần (lỗi copy-paste trong tài liệu hướng dẫn gốc) — bản sửa chỉ giữ 1 annotation.

### Cần thêm import

```java
import java.util.Optional;
```
(thường đã có sẵn nếu bạn dùng `Optional` ở chỗ khác trong file)

---

## Bước 2 — Sửa `VNPayService.java` — Thêm đối soát qua `querydr`

**File:** `src/main/java/com/fighteasy/service/VNPayService.java`

### 2.1 — Thêm field `apiUrl` (đây là phần bạn hỏi — chưa được khai báo)

Trong file hướng dẫn gốc (`huong-dan-05-payment-vnpay.md`), `application.yml` đã có sẵn key này:

```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE}
  hash-secret: ${VNPAY_HASH_SECRET}
  payment-url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  api-url: https://sandbox.vnpayment.vn/merchant_webapi/api/transaction   # ← Key này
  return-url: http://localhost:3000/payment/result
```

Nhưng `VNPayService` ban đầu **chỉ inject `payment-url`** (dùng cho `createPaymentUrl`), không inject `api-url` vì lúc đó chưa cần gọi `querydr`. Đó là lý do bạn không thấy nó được truyền vào đâu — nó tồn tại trong config nhưng chưa có field tương ứng trong service.

Tìm đoạn field declaration ở đầu `VNPayService`:

```java
@Value("${vnpay.tmn-code}")
private String tmnCode;

@Value("${vnpay.hash-secret}")
private String hashSecret;

@Value("${vnpay.payment-url}")
private String paymentUrl;
```

Thêm vào ngay dưới:

```java
@Value("${vnpay.api-url}")
private String apiUrl;
```

### 2.2 — Thêm method `queryTransactionStatus()`

Thêm method mới vào `VNPayService`, đặt sau method `createPaymentUrl()`:

```java
// ============ ĐỐI SOÁT GIAO DỊCH (querydr) ============
// Gọi khi nghi ngờ IPN bị miss — hỏi trực tiếp VNPay xem giao dịch thực tế thế nào

@SuppressWarnings("unchecked")
public Map<String, Object> queryTransactionStatus(Payment payment) {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Version", "2.1.0");
    params.put("vnp_Command", "querydr");
    params.put("vnp_TmnCode", tmnCode);
    params.put("vnp_TxnRef", payment.getVnpTxnRef());
    params.put("vnp_OrderInfo", "Kiem tra giao dich " + payment.getVnpTxnRef());
    params.put("vnp_TransactionDate",
        payment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
    params.put("vnp_CreateDate",
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
    params.put("vnp_IpAddr", "127.0.0.1");
    params.put("vnp_RequestId", UUID.randomUUID().toString());

    String hashData = params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    params.put("vnp_SecureHash", hmacSha512(hashSecret, hashData));

    // apiUrl lấy từ application.yml: vnpay.api-url
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

    ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
    return response.getBody();
}

// ============ XÁC NHẬN TỪ KẾT QUẢ ĐỐI SOÁT (không qua IPN) ============
// Dùng khi querydr xác nhận giao dịch SUCCESS nhưng IPN gốc không tới được server

@Transactional
public void confirmFromReconciliation(Payment payment, Map<String, Object> queryResult) {
    // Idempotent check — phòng trường hợp đã được xử lý ở giữa lúc scheduler đang chạy
    if (payment.getStatus() != PaymentStatus.PENDING) {
        return;
    }

    payment.setVnpTransactionNo((String) queryResult.get("vnp_TransactionNo"));
    payment.setVnpResponseCode((String) queryResult.get("vnp_ResponseCode"));
    payment.setVnpBankCode((String) queryResult.getOrDefault("vnp_BankCode", ""));
    payment.setStatus(PaymentStatus.SUCCESS);

    try {
        payment.setRawIpnData(new ObjectMapper().writeValueAsString(queryResult));
    } catch (Exception ignored) {}

    Booking booking = payment.getBooking();

    if (booking.getStatus() == BookingStatus.CONFIRMED) {
        // Booking đã được 1 payment khác confirm trước đó -> đây là tiền thừa, cần refund tay
        log.error("DUPLICATE PAYMENT phát hiện qua reconciliation — booking {} đã CONFIRMED, " +
                   "txnRef={} cần được hoàn tiền thủ công, amount={}",
            booking.getPnrCode(), payment.getVnpTxnRef(), payment.getAmount());
        // TODO: bắn alert (Slack/email) cho admin xử lý hoàn tiền
    } else {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        booking.setExpiresAt(null);
        bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingConfirmedEvent(booking));
    }

    paymentRepository.save(payment);
    log.info("Reconciled payment {} -> SUCCESS (qua querydr, IPN gốc bị miss)", payment.getVnpTxnRef());
}
```

### 2.3 — Import cần thêm vào đầu file `VNPayService.java`

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
```

(`TreeMap`, `LocalDateTime`, `DateTimeFormatter`, `Collectors`, `ObjectMapper` đã có sẵn từ trước trong file này theo hướng dẫn gốc)

> **Ghi chú:** Có thể dùng `RestClient` (Spring 6+) thay cho `RestTemplate` nếu project bạn ưu tiên API mới hơn — về logic không đổi, chỉ khác cách gọi HTTP.

---

## Bước 3 — Sửa `PaymentRepository.java` — Thêm query tìm Payment kẹt

**File:** `src/main/java/com/fighteasy/repository/PaymentRepository.java`

Tìm:

```java
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Payment> findLatestByBookingId(@Param("bookingId") Long bookingId);
}
```

Thêm method mới vào trong interface:

```java
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Payment> findLatestByBookingId(@Param("bookingId") Long bookingId);

    // Dùng cho Reconciliation Scheduler — tìm các Payment PENDING quá lâu, nghi ngờ IPN bị miss
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime time);
}
```

Đảm bảo có import `java.util.List` và `java.time.LocalDateTime` ở đầu file (thường đã có).

---

## Bước 4 — Tạo file mới `PaymentReconciliationScheduler.java`

**File mới:** `src/main/java/com/fighteasy/scheduler/PaymentReconciliationScheduler.java`

(đặt cùng package `scheduler` với `BookingExpiryScheduler`, `EmailRetryScheduler` đã có từ trước)

```java
package com.fighteasy.scheduler;

import com.fighteasy.entity.Payment;
import com.fighteasy.enums.PaymentStatus;
import com.fighteasy.repository.PaymentRepository;
import com.fighteasy.service.VNPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    private final PaymentRepository paymentRepository;
    private final VNPayService vnPayService;

    // Chạy mỗi 5 phút — quét các Payment PENDING quá 3 phút (đủ thời gian để IPN bình thường về tới)
    @Scheduled(fixedDelay = 300_000)
    public void reconcilePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(3);
        List<Payment> stuck = paymentRepository
            .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        if (stuck.isEmpty()) return;

        log.info("Reconciliation: tìm thấy {} payment PENDING nghi ngờ IPN bị miss", stuck.size());

        for (Payment payment : stuck) {
            try {
                Map<String, Object> result = vnPayService.queryTransactionStatus(payment);
                String responseCode = (String) result.get("vnp_ResponseCode");
                String transStatus  = (String) result.get("vnp_TransactionStatus");

                if ("00".equals(responseCode) && "00".equals(transStatus)) {
                    // VNPay xác nhận giao dịch thực tế đã thành công nhưng IPN không tới được server
                    vnPayService.confirmFromReconciliation(payment, result);

                } else if ("01".equals(transStatus)) {
                    // Giao dịch vẫn đang xử lý phía VNPay/ngân hàng — bỏ qua, kiểm tra lại ở lần chạy sau
                    log.debug("Payment {} vẫn đang xử lý (transStatus=01), kiểm tra lại sau", payment.getVnpTxnRef());

                } else {
                    // VNPay xác nhận giao dịch thất bại / không tồn tại -> đánh fail luôn, tránh quét lại mãi
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("Payment {} được xác nhận FAILED qua reconciliation (responseCode={})",
                        payment.getVnpTxnRef(), responseCode);
                }
            } catch (Exception e) {
                log.error("Reconciliation lỗi cho payment {}: {}", payment.getVnpTxnRef(), e.getMessage());
                // Không throw — để vòng lặp tiếp tục xử lý các payment khác, và lần chạy sau sẽ thử lại
            }
        }
    }
}
```

> **Khoảng thời gian "3 phút" và "mỗi 5 phút":** đây là giá trị khởi điểm hợp lý để test/launch ban đầu. Có thể tách ra `application.yml` (`vnpay.reconciliation.threshold-minutes`, `vnpay.reconciliation.interval-ms`) nếu muốn tinh chỉnh không cần build lại.

---

## Bước 5 — Sửa `BookingExpiryScheduler.java` — Không hủy booking đang chờ đối soát

**File:** `src/main/java/com/fighteasy/scheduler/BookingExpiryScheduler.java`

### Vấn đề

Hiện tại `findExpiredPending()` trong `BookingRepository` chỉ check `status = PENDING AND expiresAt < now`, không quan tâm có Payment nào đang trong quá trình đối soát hay không. Nếu khách đã trả tiền nhưng IPN bị miss, và đúng lúc đó `BookingExpiryScheduler` quét tới trước khi `PaymentReconciliationScheduler` kịp xử lý, booking sẽ bị hủy + ghế bị release dù khách đã trả tiền thật.

### Sửa query trong `BookingRepository.java`

Tìm:

```java
@Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.expiresAt < :now")
List<Booking> findExpiredPending(@Param("now") LocalDateTime now);
```

Thay bằng (loại trừ các booking có Payment PENDING — để dành cho Reconciliation xử lý trước):

```java
@Query("""
    SELECT b FROM Booking b
    WHERE b.status = 'PENDING' AND b.expiresAt < :now
      AND NOT EXISTS (
          SELECT 1 FROM Payment p
          WHERE p.booking = b AND p.status = 'PENDING'
      )
""")
List<Booking> findExpiredPending(@Param("now") LocalDateTime now);
```

Cách này đảm bảo: nếu có Payment đang PENDING (tức là có thể IPN đang trên đường về, hoặc đang chờ Reconciliation xác minh), Booking đó sẽ **không** bị tự hủy, dù đã quá `expiresAt`. `PaymentReconciliationScheduler` sẽ là nơi quyết định cuối cùng: confirm nếu VNPay xác nhận thành công, hoặc đánh `FAILED` cho Payment đó — và khi Payment đã `FAILED`, lần quét tiếp theo của `BookingExpiryScheduler` sẽ hủy booking như bình thường.

---

## Bước 6 — Xác nhận lại `application.yml` đã có `api-url`

**File:** `src/main/resources/application.yml`

Đảm bảo có đủ các key dưới `vnpay:` (phần `api-url` đã có sẵn từ tài liệu gốc Service 05, chỉ cần xác nhận chưa bị xoá nhầm):

```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE}
  hash-secret: ${VNPAY_HASH_SECRET}
  payment-url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  api-url: https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
  return-url: http://localhost:3000/payment/result
```

---

## Thứ tự test lại sau khi fix

| # | Hành động | Kết quả mong đợi |
|---|-----------|-------------------|
| 1 | Tạo booking → gọi `/vnpay/create` 2 lần liên tiếp | Lần 2 trả về **cùng `txnRef`** với lần 1, không tạo Payment mới |
| 2 | Kiểm tra DB `SELECT COUNT(*) FROM payments WHERE booking_id = X` sau bước 1 | Chỉ có **1** record |
| 3 | Thanh toán thành công qua link đó | Booking → `CONFIRMED`, chỉ 1 giao dịch thật trên VNPay |
| 4 | Tạo booking mới, đổi sai IPN URL (mô phỏng ngrok lỗi) → thanh toán thành công | Payment vẫn `PENDING` ngay sau đó (vì IPN không tới) |
| 5 | Đợi > 3 phút → đợi Scheduler chạy (hoặc gọi tay method để test nhanh) | Payment tự chuyển `SUCCESS`, Booking tự chuyển `CONFIRMED` qua `queryTransactionStatus` |
| 6 | Trong lúc Payment đang PENDING (trước khi Scheduler kịp chạy) — kiểm tra `BookingExpiryScheduler` có chạy đúng lúc booking quá `expiresAt` | Booking **không** bị chuyển `EXPIRED`, ghế không bị release |
| 7 | Test case Payment thật sự thất bại (responseCode khác `00` khi querydr) | Payment → `FAILED`, lần quét tiếp theo của `BookingExpiryScheduler` mới hủy booking |

---

## Lưu ý quan trọng

- `queryTransactionStatus` gọi VNPay bằng `vnp_Command=querydr` — đây là API server-to-server, không qua signature verify từ phía client nên không cần lo bị giả mạo như IPN; tuy vậy `confirmFromReconciliation` vẫn giữ idempotent check (`status != PENDING`) để an toàn nếu IPN gốc và Reconciliation chạy đua nhau (race condition giữa 2 luồng).
- Khi double-payment thật sự đã xảy ra trước khi áp fix (trường hợp bạn gặp ở câu hỏi trước) — phần `confirmFromReconciliation` chỉ tự phát hiện và **log** chứ chưa tự động hoàn tiền; việc hoàn tiền cần gọi API refund của VNPay (`vnp_Command=refund`) hoặc xử lý ngoài hệ thống, không nằm trong phạm vi fix này.
- Khoảng `3 phút` / `5 phút` ở Bước 4 nên được rút ngắn khi test (vd 30s/1 phút) để không phải đợi lâu, rồi chỉnh lại giá trị hợp lý trước khi deploy production.
