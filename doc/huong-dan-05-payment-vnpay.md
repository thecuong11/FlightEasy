# Hướng dẫn Implement Service 05 — Thanh toán VNPay

> **Module:** Payment Service | **Stack:** Spring Boot + PostgreSQL + VNPay  
> **Dựa theo spec:** `05-payment-vnpay.md` v1.0  
> **Phụ thuộc:** Service 04 (Booking) phải hoàn thành trước

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Đăng ký VNPay Sandbox & cấu hình |
| 2 | Thêm dependency |
| 3 | Database Schema & Entity |
| 4 | DTO |
| 5 | VNPayService — Tạo link & xử lý IPN |
| 6 | PaymentService |
| 7 | Controller |
| 8 | Exception Handling |
| 9 | Test với ngrok |

---

## Bước 1 — Đăng ký VNPay Sandbox & cấu hình

1. Truy cập https://sandbox.vnpayment.vn/devreg/ → đăng ký tài khoản Sandbox
2. Sau khi đăng ký, lấy:
   - **TmnCode** (Mã website)
   - **HashSecret** (Chuỗi bí mật)

### 1.1 Cấu hình `application.yml`

```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE}
  hash-secret: ${VNPAY_HASH_SECRET}
  payment-url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  api-url: https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
  return-url: http://localhost:3000/payment/result   # URL frontend

# Biến môi trường trong .env hoặc application-local.yml
# VNPAY_TMN_CODE=XXXXXXXX
# VNPAY_HASH_SECRET=XXXXXXXXXXXXXXXX
```

---

## Bước 2 — Thêm dependency

```xml
<!-- Apache Commons Codec để tạo HMAC SHA512 -->
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
</dependency>
```

---

## Bước 3 — Database Schema & Entity

### 3.1 Tạo bảng

```sql
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT NOT NULL REFERENCES bookings(id),
    amount              DECIMAL(12,2) NOT NULL,
    amount_vnpay        BIGINT NOT NULL,           -- amount × 100
    currency            CHAR(3) DEFAULT 'VND',
    gateway             VARCHAR(20) DEFAULT 'VNPAY',
    status              VARCHAR(20) DEFAULT 'PENDING',
    vnp_txn_ref         VARCHAR(100) UNIQUE,       -- Mã giao dịch FlightEasy
    vnp_transaction_no  VARCHAR(100),              -- Mã giao dịch VNPay
    vnp_bank_code       VARCHAR(20),
    vnp_card_type       VARCHAR(20),
    vnp_response_code   VARCHAR(5),
    vnp_secure_hash     TEXT,
    raw_ipn_data        JSONB,
    raw_return_data     JSONB,
    refund_amount       DECIMAL(12,2),
    refund_trans_id     VARCHAR(100),
    refunded_at         TIMESTAMP,
    refunded_by         BIGINT REFERENCES users(id),
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_vnp_txn ON payments(vnp_txn_ref);
CREATE INDEX idx_payments_status ON payments(status);
```

### 3.2 Entity: Payment

```java
@Entity
@Table(name = "payments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Long amountVnpay;      // amount × 100

    private String currency = "VND";
    private String gateway = "VNPAY";

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(unique = true)
    private String vnpTxnRef;      // PNR + timestamp

    private String vnpTransactionNo;
    private String vnpBankCode;
    private String vnpCardType;
    private String vnpResponseCode;
    private String vnpSecureHash;

    @Column(columnDefinition = "jsonb")
    private String rawIpnData;

    @Column(columnDefinition = "jsonb")
    private String rawReturnData;

    private BigDecimal refundAmount;
    private String refundTransId;
    private LocalDateTime refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private User refundedBy;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;
}
```

### 3.3 Enum: PaymentStatus

```java
public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED
}
```

---

## Bước 4 — DTO

```java
// Request tạo link thanh toán
public record CreatePaymentRequest(
    @NotBlank String pnrCode,
    @NotBlank String returnUrl,
    String ipAddress
) {}

// Response trả về link
public record CreatePaymentResponse(
    String paymentUrl,
    String txnRef,
    BigDecimal amount,
    LocalDateTime expiresAt
) {}

// Response trạng thái thanh toán
public record PaymentStatusResponse(
    String pnrCode,
    String status,
    BigDecimal amount,
    String bankCode,
    LocalDateTime paidAt
) {}
```

---

## Bước 5 — VNPayService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.payment-url}")
    private String paymentUrl;

    // ============ TẠO LINK THANH TOÁN ============

    public String createPaymentUrl(Booking booking, Payment payment, String returnUrl, String ipAddress) {
        // BR-10: Tiền VNPay = VNĐ × 100
        long amount = booking.getTotalPrice()
                .multiply(BigDecimal.valueOf(100)).longValue();

        // TreeMap để tự sort key theo alphabet — QUAN TRỌNG cho HMAC
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getVnpTxnRef());
        params.put("vnp_OrderInfo", "Thanh toan ve may bay - " + booking.getPnrCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress != null ? ipAddress : "127.0.0.1");
        params.put("vnp_CreateDate",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate",
            LocalDateTime.now().plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        // Tạo chuỗi hash (URL encode từng value)
        String hashData = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        // Ký HMAC-SHA512
        String secureHash = hmacSha512(hashSecret, hashData);
        params.put("vnp_SecureHash", secureHash);

        // Build URL cuối cùng
        String queryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return paymentUrl + "?" + queryString;
    }

    // ============ XỬ LÝ IPN (QUAN TRỌNG NHẤT) ============

    public String processIPN(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef      = params.get("vnp_TxnRef");
        long   amount      = Long.parseLong(params.get("vnp_Amount"));

        // ① Verify chữ ký HMAC trước khi làm bất cứ điều gì
        if (!verifySignature(params, receivedHash)) {
            log.warn("IPN: Invalid signature for txnRef={}", txnRef);
            return buildIPNResponse("97", "Invalid signature");
        }

        // ② Tìm payment record (trả về lỗi nếu không tìm thấy)
        Payment payment = paymentRepository.findByVnpTxnRef(txnRef).orElse(null);
        if (payment == null) {
            return buildIPNResponse("01", "Order not found");
        }

        // ③ Idempotent — tránh xử lý 2 lần cùng 1 IPN (BR-05)
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return buildIPNResponse("02", "Order already confirmed");
        }

        // ④ Kiểm tra số tiền khớp
        if (payment.getAmountVnpay() != amount) {
            log.error("IPN: Amount mismatch. Expected={}, Got={}", payment.getAmountVnpay(), amount);
            return buildIPNResponse("04", "Invalid amount");
        }

        // ⑤ Lưu raw IPN data để đối soát (BR-09)
        try {
            payment.setRawIpnData(new ObjectMapper().writeValueAsString(params));
        } catch (Exception ignored) {}

        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setVnpResponseCode(responseCode);
        payment.setVnpBankCode(params.get("vnp_BankCode"));
        payment.setVnpCardType(params.get("vnp_CardType"));

        // ⑥ Xử lý kết quả
        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            // Cập nhật booking → CONFIRMED (BR-06)
            confirmBooking(payment.getBooking());
            // Publish event để gửi email async — không block IPN response
            eventPublisher.publishEvent(new BookingConfirmedEvent(payment.getBooking()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            // Booking vẫn PENDING — user có thể thử lại (BR-07)
        }

        paymentRepository.save(payment);

        // VNPay yêu cầu phải trả về "00" nếu xử lý thành công
        return buildIPNResponse("00", "Confirm success");
    }

    // ============ XỬ LÝ RETURN URL (chỉ để hiển thị UI) ============

    public boolean verifyReturnUrl(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        return verifySignature(params, receivedHash);
    }

    // ============ HELPERS ============

    private boolean verifySignature(Map<String, String> params, String receivedHash) {
        // Bỏ vnp_SecureHash, sort theo key, build lại hashData
        String hashData = params.entrySet().stream()
                .filter(e -> !e.getKey().equals("vnp_SecureHash")
                          && !e.getKey().equals("vnp_SecureHashType"))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String expectedHash = hmacSha512(hashSecret, hashData);
        return expectedHash.equalsIgnoreCase(receivedHash);
    }

    // Import cần thiết:
    // import javax.crypto.Mac;
    // import javax.crypto.spec.SecretKeySpec;
    // import org.apache.commons.codec.binary.Hex;
    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"
            );
            mac.init(secretKey);
            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            // Hex từ commons-codec: org.apache.commons.codec.binary.Hex
            return Hex.encodeHexString(hmacData);
        } catch (Exception e) {
            throw new RuntimeException("Cannot generate HMAC", e);
        }
    }

    // Event publish khi booking confirmed
    // BookingConfirmedEvent phải được tạo trong package event:
    // public class BookingConfirmedEvent {
    //     private final Booking booking;
    //     public BookingConfirmedEvent(Booking booking) { this.booking = booking; }
    //     public Booking getBooking() { return booking; }
    // }

    private String buildIPNResponse(String rspCode, String message) {
        return "{\"RspCode\":\"" + rspCode + "\",\"Message\":\"" + message + "\"}";
    }

    private void confirmBooking(Booking booking) {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        booking.setExpiresAt(null); // Xóa thời hạn sau khi xác nhận
        bookingRepository.save(booking);
    }
}
```

---

## Bước 6 — PaymentService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final VNPayService vnPayService;

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

    public PaymentStatusResponse getPaymentStatus(String pnrCode) {
        Booking booking = bookingRepository.findByPnrCode(pnrCode)
                .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));

        Payment payment = paymentRepository.findLatestByBookingId(booking.getId())
                .orElseThrow(() -> new NotFoundException("Chưa có giao dịch thanh toán"));

        return new PaymentStatusResponse(
            pnrCode, payment.getStatus().name(),
            payment.getAmount(), payment.getVnpBankCode(),
            payment.getUpdatedAt()
        );
    }
}
```

---

## Bước 7 — Repository

```java
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);

    // Lấy payment mới nhất của booking
    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Payment> findLatestByBookingId(@Param("bookingId") Long bookingId);
}
```

---

## Bước 8 — Controller

```java
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final VNPayService vnPayService;

    // Tạo link thanh toán
    @PostMapping("/vnpay/create")
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getHeader("X-Forwarded-For");
        if (clientIp == null) clientIp = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(paymentService.createPaymentLink(request, clientIp));
    }

    // IPN — VNPay gọi vào đây (server-to-server)
    // QUAN TRỌNG: Endpoint này public, không cần auth
    @GetMapping("/vnpay/ipn")
    public ResponseEntity<String> handleIPN(@RequestParam Map<String, String> params) {
        log.info("VNPay IPN received: txnRef={}", params.get("vnp_TxnRef"));
        String response = vnPayService.processIPN(params);
        return ResponseEntity.ok(response);
    }

    // Return URL — user được redirect về sau khi thanh toán
    @GetMapping("/vnpay/return")
    public ResponseEntity<Map<String, Object>> handleReturn(
            @RequestParam Map<String, String> params) {
        boolean isValid = vnPayService.verifyReturnUrl(params);
        String responseCode = params.get("vnp_ResponseCode");

        // Chỉ verify chữ ký, KHÔNG cập nhật DB ở đây (để IPN xử lý)
        Map<String, Object> result = Map.of(
            "isValid", isValid,
            "responseCode", responseCode,
            "success", "00".equals(responseCode) && isValid,
            "txnRef", params.getOrDefault("vnp_TxnRef", "")
        );
        return ResponseEntity.ok(result);
    }

    // Kiểm tra trạng thái thanh toán (FE polling sau khi từ VNPay về)
    @GetMapping("/status/{pnr}")
    public ResponseEntity<PaymentStatusResponse> getStatus(@PathVariable String pnr) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(pnr));
    }
}
```

Thêm `/api/v1/payments/vnpay/ipn` và `/api/v1/payments/vnpay/return` vào `WHITE_LIST_API` trong `SecurityConfig`:

```java
public static String[] WHITE_LIST_API = {
    "/api/v1/auth/**",
    "/api/v1/payments/vnpay/ipn",
    "/api/v1/payments/vnpay/return"
};
```

---

## Bước 9 — Custom Exception

```java
public class InvalidPaymentException extends RuntimeException {
    public InvalidPaymentException(String msg) { super(msg); }
}
```

Thêm vào `GlobalExceptionHandler`:

```java
@ExceptionHandler(InvalidPaymentException.class)
public ResponseEntity<?> handleInvalidPayment(InvalidPaymentException ex) {
    return ResponseEntity.status(400)
            .body(Map.of("code", "INVALID_PAYMENT", "message", ex.getMessage()));
}
```

---

## Bước 10 — Test với Postman + ngrok

### 10.1 Cài ngrok để expose localhost

```bash
# Cài ngrok
brew install ngrok  # macOS
# hoặc download từ https://ngrok.com

# Chạy
ngrok http 8080
# → Lấy URL dạng: https://abc123.ngrok.io
```

### 10.2 Cập nhật IPN URL trong VNPay Sandbox

Vào sandbox dashboard → cập nhật IPN URL thành:
```
https://abc123.ngrok.io/api/v1/payments/vnpay/ipn
```

### 10.3 Test card VNPay Sandbox

```
Ngân hàng:      NCB
Số thẻ:         9704198526191432198
Tên:            NGUYEN VAN A
Ngày phát hành: 07/15
OTP:            123456
```

### 10.4 Thứ tự test

| # | Request | Kết quả mong đợi |
|---|---------|-----------------|
| 1 | `POST /bookings` để tạo booking PENDING | `201` + pnrCode |
| 2 | `POST /payments/vnpay/create` với pnrCode | `200` + paymentUrl |
| 3 | Mở paymentUrl trên browser → nhập test card | Redirect về returnUrl |
| 4 | `GET /payments/status/{pnr}` | `status: SUCCESS` |
| 5 | Kiểm tra booking | Status = `CONFIRMED` |
| 6 | `POST /payments/vnpay/create` với booking đã confirm | `400 INVALID_PAYMENT` |

---

## Lưu ý quan trọng

- **Chỉ xử lý DB trong IPN**, không trong Return URL — user có thể giả mạo tham số Return URL
- `TreeMap` là bắt buộc khi build params (sort key theo alphabet) để HMAC khớp
- Lưu `rawIpnData` vào DB để đối soát nếu có tranh chấp với VNPay
- IPN có thể gọi nhiều lần — idempotent check (`status != PENDING`) ngăn xử lý 2 lần
- Môi trường production dùng URL thật: `https://vnpayment.vn/paymentv2/vpcpay.html`
