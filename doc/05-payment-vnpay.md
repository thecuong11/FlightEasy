# Service 05 — Thanh toán VNPay (Payment)

> **Module:** Payment Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P0 — Doanh thu của hệ thống

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Sau khi tạo booking thành công, người dùng có 15 phút để thanh toán qua VNPay. Hệ thống tạo link thanh toán, redirect người dùng sang VNPay, xử lý callback và cập nhật trạng thái booking.

### 1.2 VNPay hoạt động như thế nào

```
FlightEasy ──► tạo link thanh toán ──► redirect user sang VNPay
     ▲                                          │
     │                                          ▼
     └──── callback (IPN) ◄──── user nhập thẻ & xác nhận
```

VNPay có 2 loại callback:
- **Return URL:** Browser redirect về sau khi user hoàn tất (hiển thị kết quả)
- **IPN (Instant Payment Notification):** Server-to-server, VNPay gọi vào backend để xác nhận thanh toán thực sự

> **Quan trọng:** Chỉ xử lý IPN là đáng tin cậy. Return URL chỉ dùng để redirect UI, không dùng để cập nhật DB vì user có thể giả mạo tham số.

### 1.3 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Chỉ cho phép thanh toán booking có status = PENDING |
| BR-02 | Booking phải còn hạn (chưa hết 15 phút) |
| BR-03 | Mỗi lần tạo link tạo 1 bản ghi Payment (trạng thái PENDING) |
| BR-04 | Xác minh chữ ký HMAC-SHA512 từ VNPay trước khi xử lý |
| BR-05 | Idempotent: nếu IPN gọi lại lần 2 (trùng transaction ID) → bỏ qua |
| BR-06 | Sau thanh toán thành công: booking → CONFIRMED, gửi email xác nhận |
| BR-07 | Sau thanh toán thất bại: booking vẫn PENDING, user có thể thử lại |
| BR-08 | Hoàn tiền chỉ Admin mới được thực hiện |
| BR-09 | Lưu toàn bộ raw response từ VNPay để đối soát |
| BR-10 | Tiền VNPay tính bằng VNĐ × 100 (VD: 1,500,000 VNĐ → gửi 150000000) |

### 1.4 Vòng đời Payment

```
         [Tạo link]
              │
              ▼
          PENDING ──── (user bỏ qua / timeout) ──► CANCELLED
              │
     [VNPay IPN callback]
         /        \
        /          \
    SUCCESS       FAILED
        │
   [booking → CONFIRMED]
   [gửi email]
        │
   (nếu hủy sau)
        │
        ▼
    REFUNDED
```

---

## 2. Flow Diagram

### 2.1 Flow Thanh toán VNPay đầy đủ

```
User        FlightEasy FE    FlightEasy BE       VNPay
  │               │                │               │
  │ Click "Thanh  │                │               │
  │ toán ngay"    │                │               │
  │──────────────►│                │               │
  │               │ POST /payments │               │
  │               │ /vnpay/create  │               │
  │               │ {pnrCode}      │               │
  │               │───────────────►│               │
  │               │                │ buildVNPayURL()
  │               │                │ (params + HMAC sign)
  │               │                │               │
  │               │◄───────────────│               │
  │               │ {paymentUrl}   │               │
  │◄──────────────│                │               │
  │ Redirect sang VNPay            │               │
  │───────────────────────────────────────────────►│
  │               │                │               │
  │        [Nhập thông tin thẻ]    │               │
  │               │                │               │
  │               │     ┌──────────────────────────►│ IPN (server→server)
  │               │     │          │◄──────────────│ POST /payments/vnpay/ipn
  │               │     │          │               │ {vnp_ResponseCode=00, ...}
  │               │     │          │ verifySignature()
  │               │     │          │ idempotentCheck()
  │               │     │          │ confirmBooking()
  │               │     │          │ sendEmail() [async]
  │               │     │          │ respond "00" to VNPay
  │               │     │          │               │
  │               │ Return URL     │               │
  │◄──────────────────────────────────────────────│
  │ Redirect về   │                │               │
  │ /payment/result?...            │               │
  │──────────────►│                │               │
  │               │ GET /payments  │               │
  │               │ /vnpay/verify  │               │
  │               │ ?pnr=AB3X9K    │               │
  │               │───────────────►│               │
  │               │◄───────────────│               │
  │               │ {status: SUCCESS}              │
  │ Hiển thị trang xác nhận       │               │
```

### 2.2 Flow Hoàn tiền (Admin)

```
Admin           PaymentController    PaymentService      VNPay API
  │                   │                   │                  │
  │ POST /admin/       │                   │                  │
  │ payments/{id}      │                   │                  │
  │ /refund            │                   │                  │
  │──────────────────►│                   │                  │
  │                   │ processRefund()    │                  │
  │                   │──────────────────►│                  │
  │                   │                   │ checkRefundable()│
  │                   │                   │ (còn trong 365 ngày?)
  │                   │                   │ callVNPayRefund()│
  │                   │                   │─────────────────►│
  │                   │                   │◄─────────────────│
  │                   │                   │ {refundTransId}  │
  │                   │                   │ updatePayment(REFUNDED)
  │                   │                   │ updateBooking(REFUNDED)
  │                   │                   │ notifyUser() [async]
  │◄──────────────────│                   │                  │
  │ 200 OK             │                   │                  │
  │ {refundAmount,     │                   │                  │
  │  refundTransId}    │                   │                  │
```

---

## 3. Database Schema

```sql
-- Thanh toán
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT NOT NULL REFERENCES bookings(id),
    amount              DECIMAL(12,2) NOT NULL,
    amount_vnpay        BIGINT NOT NULL,            -- amount × 100
    currency            CHAR(3) DEFAULT 'VND',
    gateway             VARCHAR(20) DEFAULT 'VNPAY',
    status              VARCHAR(20) DEFAULT 'PENDING',
    -- VNPay fields
    vnp_txn_ref         VARCHAR(100) UNIQUE,        -- Mã giao dịch phía FlightEasy
    vnp_transaction_no  VARCHAR(100),               -- Mã giao dịch phía VNPay
    vnp_bank_code       VARCHAR(20),                -- VIETCOMBANK, TECHCOMBANK...
    vnp_card_type       VARCHAR(20),                -- ATM | CREDIT
    vnp_response_code   VARCHAR(5),                 -- 00 = success
    vnp_secure_hash     TEXT,                       -- Chữ ký từ VNPay
    -- Raw response để đối soát
    raw_ipn_data        JSONB,
    raw_return_data     JSONB,
    -- Refund
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

---

## 4. API Specification

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| POST | `/api/v1/payments/vnpay/create` | USER | Tạo link thanh toán VNPay |
| GET | `/api/v1/payments/vnpay/ipn` | Public* | Callback từ VNPay server (IPN) |
| GET | `/api/v1/payments/vnpay/return` | Public | Callback khi user quay lại |
| GET | `/api/v1/payments/status/{pnr}` | USER | Kiểm tra trạng thái thanh toán |
| POST | `/api/v1/admin/payments/{id}/refund` | ADMIN | Hoàn tiền |

*IPN endpoint public nhưng phải verify HMAC signature

### Request — Tạo link thanh toán

```json
POST /api/v1/payments/vnpay/create
{
  "pnrCode": "AB3X9K",
  "returnUrl": "https://flighteasy.vn/payment/result",
  "ipAddress": "118.70.xx.xx"
}
```

### Response — Link thanh toán

```json
{
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=155500000&vnp_Command=pay&...",
  "txnRef": "AB3X9K-1717920000",
  "amount": 1555000,
  "expiresAt": "2025-06-10T14:30:00"
}
```

---

## 5. Code mẫu

### VNPayService — Tạo URL thanh toán

```java
@Service
public class VNPayService {

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.payment-url}")
    private String paymentUrl;

    @Value("${vnpay.api-url}")
    private String apiUrl;

    public String createPaymentUrl(Booking booking, String returnUrl, String ipAddress) {
        String txnRef = booking.getPnrCode() + "-" + System.currentTimeMillis();
        long amount = booking.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, String> params = new TreeMap<>(); // TreeMap tự sort key — quan trọng cho HMAC
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan ve may bay - " + booking.getPnrCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate", LocalDateTime.now().plusMinutes(15)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        // Tạo chuỗi hash data từ toàn bộ params (đã sort)
        String hashData = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String secureHash = HmacUtils.hmacSha512Hex(hashSecret, hashData);
        params.put("vnp_SecureHash", secureHash);

        // Build query string
        String queryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        // Lưu txnRef vào Payment record
        savePaymentRecord(booking, txnRef, amount);

        return paymentUrl + "?" + queryString;
    }

    // Xử lý IPN — quan trọng nhất
    @Transactional
    public String processIPN(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef");
        String vnpTransactionNo = params.get("vnp_TransactionNo");
        long amount = Long.parseLong(params.get("vnp_Amount"));

        // ① Verify chữ ký HMAC
        if (!verifySignature(params, receivedHash)) {
            log.warn("IPN: Invalid signature for txnRef={}", txnRef);
            return buildIPNResponse("97", "Invalid signature"); // 97 = chữ ký sai
        }

        // ② Tìm payment record
        Payment payment = paymentRepository.findByVnpTxnRef(txnRef)
                .orElse(null);
        if (payment == null) {
            return buildIPNResponse("01", "Order not found");
        }

        // ③ Idempotent check — tránh xử lý 2 lần
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return buildIPNResponse("02", "Order already confirmed");
        }

        // ④ Kiểm tra số tiền khớp
        if (payment.getAmountVnpay() != amount) {
            return buildIPNResponse("04", "Invalid amount");
        }

        // ⑤ Xử lý kết quả
        payment.setVnpTransactionNo(vnpTransactionNo);
        payment.setVnpResponseCode(responseCode);
        payment.setRawIpnData(params);

        if ("00".equals(responseCode)) {
            // Thanh toán thành công
            payment.setStatus(PaymentStatus.SUCCESS);
            confirmBooking(payment.getBooking());
            // Gửi email async — không block IPN response
            eventPublisher.publishEvent(new BookingConfirmedEvent(payment.getBooking()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);
        return buildIPNResponse("00", "Confirm success"); // Luôn trả 00 nếu xử lý OK
    }

    private boolean verifySignature(Map<String, String> params, String receivedHash) {
        // Tạo lại hash từ params (bỏ vnp_SecureHash)
        String hashData = params.entrySet().stream()
                .filter(e -> !e.getKey().equals("vnp_SecureHash"))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String expectedHash = HmacUtils.hmacSha512Hex(hashSecret, hashData);
        return expectedHash.equalsIgnoreCase(receivedHash);
    }

    private String buildIPNResponse(String rspCode, String message) {
        return "{\"RspCode\":\"" + rspCode + "\",\"Message\":\"" + message + "\"}";
    }
}
```

### application.yml — VNPay config

```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE}           # Lấy từ VNPay sandbox
  hash-secret: ${VNPAY_HASH_SECRET}      # Secret key từ VNPay
  payment-url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  api-url: https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
```

---

## 6. Lưu ý quan trọng khi test

1. **Môi trường sandbox:** Dùng URL sandbox, không cần thẻ thật
2. **Test card VNPay sandbox:**
   - Ngân hàng: NCB
   - Số thẻ: `9704198526191432198`
   - Tên: `NGUYEN VAN A`
   - Ngày phát hành: `07/15`
   - OTP: `123456`
3. **IPN local:** Dùng ngrok để expose localhost cho VNPay callback
   ```bash
   ngrok http 8080
   # → VNPay IPN URL: https://abc123.ngrok.io/api/v1/payments/vnpay/ipn
   ```
4. **Verify signature luôn phải pass** trước khi xử lý bất kỳ logic nào
