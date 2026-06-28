# FlightEasy Backend — Code Review & Improvement Report

> Phân tích toàn bộ `src/main/java/com/flighteasy/`, ngày 2026-06-28  
> Stack: Spring Boot 3.5 · Java 21 · PostgreSQL · Redis · JWT · VNPay

---

## Tóm tắt mức độ nghiêm trọng

| Mức | Số lượng | Ý nghĩa |
|-----|----------|---------|
| 🔴 **Critical** | 3 | Bug gây sai dữ liệu hoặc lỗ hổng bảo mật nghiêm trọng |
| 🟠 **High** | 4 | Tính năng bị hỏng hoặc rủi ro bảo mật cao |
| 🟡 **Medium** | 5 | Có thể gây lỗi trong điều kiện nhất định |
| 🟢 **Low** | 5 | Chất lượng code, hiệu năng, maintainability |

---

## 🔴 Critical

---

### C-1 · `AuthService.refresh()` trả về access token cũ

**File:** `service/AuthService.java:87`

```java
public AuthResponse refresh(String rawToken, String accessToken, HttpServletResponse response) {
    RefreshToken newRefreshToken = refreshTokenService.rotateToken(rawToken, accessToken);
    String newAccessToken = jwtService.generateAccessToken(newRefreshToken.getUser()); // ← tạo token mới
    setRefreshTokenCookie(response, newRefreshToken.getToken());

    return new AuthResponse(accessToken, "Bearer", null); // ← BUG: trả về accessToken CŨ, không phải newAccessToken
}
```

**Lỗi gì xảy ra:**  
- Client nhận lại đúng access token cũ sau khi refresh.  
- Sau 15 phút (khi access token hết hạn), client gọi refresh lại → nhận token cũ → hết hạn ngay lập tức → người dùng bị logout không rõ nguyên nhân.  
- Refresh token đã bị rotate (marked as `isUsed=true`) nhưng client giữ token cũ không dùng được, mất hoàn toàn phiên đăng nhập.

**Sửa:**
```java
return new AuthResponse(newAccessToken, "Bearer", null);
```

---

### C-2 · `VNPayService.verifySignature()` log `hashSecret` ra console

**File:** `service/VNPayService.java:146-147`

```java
private boolean verifySignature(Map<String, String> params, String receivedHash) {
    log.info("=====hashSecret===== [{}]", hashSecret); // ← lộ secret key
    log.info("===receivedHash=== [{}]", receivedHash); // ← lộ hash của giao dịch
    ...
}
```

**Lỗi gì xảy ra:**  
- Mọi IPN callback từ VNPay và mọi lần verify return URL đều in `hashSecret` vào log.  
- Nếu log tập trung (ELK, CloudWatch, Loki…) thì bất kỳ ai có quyền đọc log đều lấy được secret key.  
- Kẻ tấn công dùng secret key để tự forge IPN hợp lệ → xác nhận thanh toán giả → lấy vé miễn phí.

**Sửa:**
```java
private boolean verifySignature(Map<String, String> params, String receivedHash) {
    // Xóa hoàn toàn 3 dòng log trên
    String hashData = params.entrySet().stream()
            .filter(e -> !e.getKey().equals("vnp_SecureHash") && !e.getKey().equals("vnp_SecureHashType"))
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    String expectedHash = hmacSha512(hashSecret, hashData);
    return expectedHash.equals(receivedHash);
}
```

---

### C-3 · `VNPayService.processIPN()` phát hiện duplicate payment nhưng vẫn confirm booking

**File:** `service/VNPayService.java:122-130`

```java
if ("00".equals(responseCode)) {
    payment.setStatus(PaymentStatus.SUCCESS);

    if (payment.getBooking().getStatus() == BookingStatus.CONFIRMED) {
        log.error("DUPLICATE PAYMENT detected..."); // ← phát hiện duplicate
        payment.setStatus(PaymentStatus.SUCCESS);   // ← set SUCCESS (thừa)
    }
    confirmBooking(payment.getBooking());            // ← VẪN gọi confirmBooking!
    eventPublisher.publishEvent(new BookingConfirmedEvent(payment.getBooking())); // ← VẪN publish event!
}
```

**Lỗi gì xảy ra:**  
- Khi VNPay gửi IPN trùng lặp (retry), code phát hiện booking đã CONFIRMED nhưng vẫn tiếp tục `confirmBooking()` và publish `BookingConfirmedEvent`.  
- Kết quả: email xác nhận đặt vé được gửi nhiều lần cho cùng một booking.  
- Nếu có logic downstream trong `BookingConfirmedEvent` (tích điểm, xuất vé…), chúng sẽ chạy trùng.

**Sửa:**
```java
if ("00".equals(responseCode)) {
    payment.setStatus(PaymentStatus.SUCCESS);
    paymentRepository.save(payment);

    if (payment.getBooking().getStatus() == BookingStatus.CONFIRMED) {
        log.warn("Duplicate IPN ignored for already-confirmed booking {}, txnRef={}",
                payment.getBooking().getPnrCode(), payment.getVnpTxnRef());
        return buildIPNResponse("02", "Order already confirmed");
    }

    confirmBooking(payment.getBooking());
    eventPublisher.publishEvent(new BookingConfirmedEvent(payment.getBooking()));
}
```

---

## 🟠 High

---

### H-1 · `AuthService.forgotPassword()` không gửi email — tính năng bị hỏng hoàn toàn

**File:** `service/AuthService.java:125`

```java
public void forgotPassword(String email) {
    userRepository.findByEmail(email).ifPresent(user -> {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                ...
                .build();
        passwordResetTokenRepository.save(resetToken);
        //emailService.sendResetEmail(user.getEmail(), resetToken.getToken()); // ← BỊ COMMENT
    });
}
```

**Lỗi gì xảy ra:**  
- Token được tạo và lưu DB nhưng không được gửi cho user.  
- Người dùng gọi `POST /api/v1/auth/forgot-password` → nhận `200 OK` → không nhận được email → không thể đặt lại mật khẩu.  
- Tính năng "quên mật khẩu" hoàn toàn không hoạt động trong môi trường production.

**Sửa:**  
Inject `EmailService` và bỏ comment:
```java
@Transactional
public void forgotPassword(String email) {
    userRepository.findByEmail(email).ifPresent(user -> {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
    });
}
```

---

### H-2 · Cookie path thiếu dấu `/` — refresh token không được gửi đúng

**File:** `service/AuthService.java:164,176`

```java
private void setRefreshTokenCookie(HttpServletResponse response, String token) {
    ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
            ...
            .path("api/v1/auth")  // ← THIẾU dấu / ở đầu
            .build();
}
```

**Lỗi gì xảy ra:**  
- RFC 6265: cookie `path` phải là **absolute path** (bắt đầu bằng `/`).  
- Với `path("api/v1/auth")`, một số trình duyệt và HTTP client sẽ **không gửi cookie** khi request đến `/api/v1/auth/refresh`.  
- Người dùng không thể refresh token → phải đăng nhập lại sau 15 phút.  
- Behavior có thể khác nhau tùy browser/client, gây bug khó reproduce.

**Sửa:**
```java
.path("/api/v1/auth")
```
Áp dụng cho cả `setRefreshTokenCookie()` và `clearRefreshTokenCookie()`.

---

### H-3 · `BookingService.cancelBookingByAdmin()` không notify user và không xử lý refund

**File:** `service/BookingService.java:297-306`

```java
@Transactional
public void cancelBookingByAdmin(String pnrCode, String reason) {
    Booking booking = bookingRepository.findByPnrCode(pnrCode)
            .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));
    booking.setStatus(BookingStatus.CANCELLED);
    booking.setCancelledAt(LocalDateTime.now());
    booking.setCancelReason(reason);
    bookingRepository.save(booking);
    releaseSeatsForBooking(booking);
    // ← không publish event, không tính refund, không gửi email
}
```

**Lỗi gì xảy ra:**  
- Khi admin hủy booking của khách hàng, khách hàng không nhận được email thông báo.  
- Không có logic tính và ghi `refundAmount` → khách không biết được hoàn bao nhiêu tiền.  
- So sánh với `cancelBooking()` của user: có `calculateRefund()`, `setRefundAmount()`, và `publishEvent()`.

**Sửa:**
```java
@Transactional
public void cancelBookingByAdmin(String pnrCode, String reason) {
    Booking booking = bookingRepository.findByPnrCode(pnrCode)
            .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));

    if (booking.getStatus() == BookingStatus.CONFIRMED) {
        booking.setRefundAmount(booking.getTotalPrice()); // hoàn 100% khi admin hủy
    }

    booking.setStatus(BookingStatus.CANCELLED);
    booking.setCancelledAt(LocalDateTime.now());
    booking.setCancelReason(reason);
    bookingRepository.save(booking);

    releaseSeatsForBooking(booking);
    eventPublisher.publishEvent(new BookingCancelledEvent(booking)); // notify user
}
```

---

### H-4 · `BookingService.getBooking()` thiếu `@Transactional` → `LazyInitializationException`

**File:** `service/BookingService.java:127-137`

```java
public BookingResponse getBooking(String pnrCode, Long userId) {  // ← không có @Transactional
    Booking booking = bookingRepository.findByPnrCode(pnrCode)
            .orElseThrow(...);
    if (!booking.getUser().getId().equals(userId)) { ... }
    FlightClass fc = booking.getSegments().stream()  // ← truy cập lazy collection
            .findFirst()...
            .getFlightClass();                        // ← truy cập lazy relation
    return toBookingResponse(booking, fc, null);
}
```

**Lỗi gì xảy ra:**  
- `segments` và `flightClass` là lazy-loaded JPA relations.  
- Không có `@Transactional` → session đã đóng khi truy cập lazy relation → `LazyInitializationException: could not initialize proxy – no Session`.  
- API `GET /api/v1/bookings/{pnr}` trả về `500 Internal Server Error`.

**Sửa:**
```java
@Transactional(readOnly = true)
public BookingResponse getBooking(String pnrCode, Long userId) {
    ...
}
```

---

## 🟡 Medium

---

### M-1 · Cache key không bao gồm `page` và `size` → trả sai trang

**File:** `service/FlightSearchService.java:216-225`

```java
private String buildCacheKey(FlightSearchRequest r) {
    String filterHash = Integer.toHexString(Objects.hash(
            r.getMinPrice(), r.getMaxPrice(), r.getAirlines(),
            r.getMaxDuration(), r.getDepartTimeRange()
    ));
    return String.format("flight-search:%s:%s:%s:%s:%d:%s:%s",
            r.getFrom(), r.getTo(), r.getDepartDate(),
            r.getClassType(), r.getTotalPassengers(),
            r.getSortBy(), filterHash);  // ← thiếu page và size
}
```

`buildResponse()` thực hiện pagination (line 180-182) **sau khi** lấy từ cache. Nhưng response lưu vào cache đã chứa dữ liệu trang 0. Request page=1 sẽ nhận kết quả page=0 từ cache.

**Sửa:**
```java
return String.format("flight-search:%s:%s:%s:%s:%d:%s:%s:p%d:s%d",
        r.getFrom(), r.getTo(), r.getDepartDate(),
        r.getClassType(), r.getTotalPassengers(),
        r.getSortBy(), filterHash, r.getPage(), r.getSize());
```

---

### M-2 · Round-trip dùng `ForkJoinPool` chung cho I/O (DB + Redis)

**File:** `service/FlightSearchService.java:105-106`

```java
CompletableFuture<FlightSearchResponse> outboundFuture =
        CompletableFuture.supplyAsync(() -> search(request));       // ← dùng ForkJoinPool.commonPool()
CompletableFuture<FlightSearchResponse> returnFuture =
        CompletableFuture.supplyAsync(() -> search(returnRequest)); // ← dùng ForkJoinPool.commonPool()
```

**Vấn đề:**  
- `ForkJoinPool.commonPool()` được thiết kế cho **CPU-bound** tasks, không phải I/O.  
- Khi nhiều request round-trip đồng thời, pool bị block bởi DB/Redis → throughput giảm mạnh.  
- Dưới tải cao có thể gây thread starvation cho toàn bộ JVM.

**Sửa:** Dùng executor riêng:
```java
// Trong AsyncConfig.java, thêm:
@Bean(name = "searchExecutor")
public Executor searchExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("search-");
    executor.initialize();
    return executor;
}

// Trong FlightSearchService:
@Qualifier("searchExecutor")
private final Executor searchExecutor;

CompletableFuture<FlightSearchResponse> outboundFuture =
        CompletableFuture.supplyAsync(() -> search(request), searchExecutor);
```

---

### M-3 · `buildIPNResponse()` tự build JSON string — rủi ro injection

**File:** `service/VNPayService.java:173-175`

```java
private String buildIPNResponse(String rspCode, String message) {
    return "{\"RspCode\":\"" + rspCode + "\",\"Message\":\"" + message + "\"}";
}
```

Nếu `message` chứa ký tự `"` hoặc `\`, response JSON sẽ bị malformed. VNPay có thể không parse được và retry IPN liên tục.

**Sửa:**
```java
private String buildIPNResponse(String rspCode, String message) {
    try {
        return objectMapper.writeValueAsString(Map.of("RspCode", rspCode, "Message", message));
    } catch (Exception e) {
        return "{\"RspCode\":\"99\",\"Message\":\"Internal error\"}";
    }
}
```

---

### M-4 · `VNPayService.queryTransactionStatus()` tạo `new RestTemplate()` mỗi lần gọi

**File:** `service/VNPayService.java:202`

```java
public Map<String, Object> queryTransactionStatus(Payment payment) {
    ...
    RestTemplate restTemplate = new RestTemplate(); // ← tạo mới mỗi lần
    ...
}
```

**Vấn đề:**  
- Không có connection pooling → mỗi request tạo TCP connection mới.  
- Không có timeout configuration → nếu VNPay API chậm, thread bị block vô thời hạn.  
- Scheduler `PaymentReconciliationScheduler` gọi hàm này cho **tất cả** pending payments → có thể mở hàng trăm connection đồng thời.

**Sửa:**
```java
// Trong config/RestTemplateConfig.java (tạo mới):
@Bean
public RestTemplate vnpayRestTemplate() {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    return new RestTemplate(factory);
}

// Inject vào VNPayService:
private final RestTemplate vnpayRestTemplate;
```

---

### M-5 · `PaymentReconciliationScheduler` threshold 3 ngày — quá dài

**File:** `scheduler/PaymentReconciliationScheduler.java:26`

```java
LocalDateTime threshold = LocalDateTime.now().minusDays(3);
List<Payment> stuck = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);
```

**Vấn đề:**  
- Booking expire sau 15 phút nhưng payment stuck phải đợi 3 ngày mới được reconcile.  
- Trong 3 ngày đó, booking ở trạng thái PENDING (hoặc đã EXPIRED nhưng payment vẫn PENDING), gây mâu thuẫn dữ liệu.  
- Nên reconcile sớm hơn — ví dụ sau 30-60 phút.

**Sửa:**
```java
LocalDateTime threshold = LocalDateTime.now().minusMinutes(60); // reconcile sau 1 giờ
```

---

## 🟢 Low / Code Quality

---

### L-1 · `WHITE_LIST_API` không phải `final` — có thể bị modify

**File:** `security/SecurityConfig.java:27`

```java
public static String[] WHITE_LIST_API = { ... }; // ← thiếu final
```

**Sửa:**
```java
public static final String[] WHITE_LIST_API = { ... };
```

---

### L-2 · `show-sql: true` và Thymeleaf cache disabled không phù hợp production

**File:** `resources/application.yml`

```yaml
jpa:
  show-sql: true          # ← log toàn bộ SQL ra console — ảnh hưởng hiệu năng
```

**File:** `config/ThymeleafEmailConfig.java`
```java
resolver.setCacheable(false); // ← parse template mỗi lần gửi email
```

**Sửa:** Dùng Spring profiles:
```yaml
# application-prod.yml
jpa:
  show-sql: false
spring:
  thymeleaf:
    cache: true
```

---

### L-3 · `generatePNR()` có thể loop vô tận (rủi ro lý thuyết)

**File:** `service/BookingService.java:223-234`

```java
do {
    // tạo PNR 6 ký tự
} while (bookingRepository.existsByPnrCode(pnr)); // ← không có giới hạn retry
```

Với ~32^6 ≈ 1 tỷ tổ hợp thì rất khó xảy ra, nhưng nên thêm giới hạn để tránh infinite loop trong trường hợp DB có vấn đề:

```java
private String generatePNR() {
    String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    for (int attempt = 0; attempt < 10; attempt++) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        String pnr = sb.toString();
        if (!bookingRepository.existsByPnrCode(pnr)) return pnr;
    }
    throw new RuntimeException("Không thể tạo PNR duy nhất sau 10 lần thử");
}
```

---

### L-4 · Không có CORS configuration

**File:** `security/SecurityConfig.java`

Không thấy `CorsConfigurationSource` bean hoặc `@CrossOrigin` nào được cấu hình. Nếu frontend ở domain khác (React dev server, production domain…), tất cả request đều bị block bởi browser CORS policy.

**Sửa:** Thêm vào `SecurityConfig`:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://flighteasy.vn", "http://localhost:3000"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true); // cần cho cookie
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}

// Trong filterChain():
http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
```

---

### L-5 · Không có rate limiting trên auth endpoints

**File:** `security/SecurityConfig.java`, `controller/AuthController.java`

`POST /api/v1/auth/login` chỉ bảo vệ bằng account lockout (sau 5 lần sai). Không có IP-level rate limiting. Attacker có thể brute-force nhiều tài khoản khác nhau (credential stuffing) mà không bị chặn.

**Đề xuất:** Thêm Bucket4j hoặc Resilience4j rate limiter:
```java
// Ví dụ với Bucket4j
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(...) {
    if (!loginRateLimiter.tryConsume(1)) {
        throw new TooManyRequestsException("Quá nhiều yêu cầu, vui lòng thử lại sau");
    }
    ...
}
```

---

## Bảng tổng hợp ưu tiên sửa

| # | File | Dòng | Vấn đề | Độ ưu tiên |
|---|------|------|---------|-----------|
| C-1 | `AuthService.java` | 87 | Trả old access token sau refresh | 🔴 Sửa ngay |
| C-2 | `VNPayService.java` | 146–147 | Log hashSecret ra console | 🔴 Sửa ngay |
| C-3 | `VNPayService.java` | 125–130 | Duplicate payment vẫn confirm booking | 🔴 Sửa ngay |
| H-1 | `AuthService.java` | 125 | Email reset password bị comment | 🟠 Sprint này |
| H-2 | `AuthService.java` | 164, 176 | Cookie path thiếu `/` | 🟠 Sprint này |
| H-3 | `BookingService.java` | 297–306 | Admin cancel không notify user | 🟠 Sprint này |
| H-4 | `BookingService.java` | 127 | getBooking thiếu @Transactional | 🟠 Sprint này |
| M-1 | `FlightSearchService.java` | 216–225 | Cache key thiếu page/size | 🟡 Sprint sau |
| M-2 | `FlightSearchService.java` | 105–106 | ForkJoinPool cho I/O | 🟡 Sprint sau |
| M-3 | `VNPayService.java` | 173–175 | Manual JSON string build | 🟡 Sprint sau |
| M-4 | `VNPayService.java` | 202 | new RestTemplate() mỗi call | 🟡 Sprint sau |
| M-5 | `PaymentReconciliationScheduler.java` | 26 | Threshold 3 ngày quá dài | 🟡 Sprint sau |
| L-1 | `SecurityConfig.java` | 27 | WHITE_LIST_API không final | 🟢 Khi rảnh |
| L-2 | `application.yml` | — | show-sql=true, cache=false | 🟢 Khi rảnh |
| L-3 | `BookingService.java` | 223 | PNR loop không giới hạn | 🟢 Khi rảnh |
| L-4 | `SecurityConfig.java` | — | Không có CORS config | 🟢 Khi rảnh |
| L-5 | `AuthController.java` | — | Không có rate limiting | 🟢 Khi rảnh |

---

## Điểm tốt — Không cần thay đổi

Những phần này được implement đúng và không cần sửa:

- **Token rotation pattern** (`RefreshTokenService`): Phát hiện token reuse và revoke tất cả sessions — implement chuẩn.
- **Pessimistic locking** (`seatRepository.findAllByIdWithLock`, `flightClassRepository.findByIdWithLock`): Tránh race condition khi nhiều user đặt cùng ghế.
- **BCrypt cost=12**: Cân bằng tốt giữa bảo mật và hiệu năng.
- **Account lockout** (`UserAttemptService` với `REQUIRES_NEW`): Dùng transaction mới riêng để tránh rollback mất thông tin failed attempts.
- **ShedLock** cho schedulers: Đúng cách để chạy distributed scheduler.
- **HTTP-only + Secure + SameSite=Strict** cookie: Bảo vệ tốt chống XSS và CSRF.
- **VNPay IPN amount verification**: Kiểm tra `amountVnpay` khớp trước khi xử lý.
- **AdminAuditAspect**: Log đầy đủ hành động admin với IP và timestamp.