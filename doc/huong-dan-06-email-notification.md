# Hướng dẫn Implement Service 06 — Email Notification

> **Module:** Notification Service | **Stack:** Spring Boot + JavaMailSender + Thymeleaf  
> **Dựa theo spec:** `06-email-notification.md` v1.0  
> **Phụ thuộc:** Service 04 (Booking) và Service 05 (Payment) phải hoàn thành trước

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Cài đặt dependency & cấu hình Gmail |
| 2 | Database Schema & Entity |
| 3 | AsyncConfig — Thread pool riêng cho email |
| 4 | ThymeleafConfig |
| 5 | EmailService — Gửi email với Thymeleaf |
| 6 | Event Listener — Lắng nghe sự kiện |
| 7 | Retry Scheduler — Gửi lại email thất bại |
| 8 | Check-in Reminder Scheduler |
| 9 | Template HTML mẫu (Thymeleaf) |
| 10 | Test |

---

## Bước 1 — Dependency & Cấu hình Gmail

### 1.1 Thêm dependency

> ⚠️ **Spring Boot 3.x:** `thymeleaf-extras-java8time` đã được tích hợp sẵn vào Thymeleaf 3.1+, **không cần thêm nữa**.

```xml
<!-- Thymeleaf -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- JavaMailSender -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- Context Support -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context-support</artifactId>
</dependency>
```

### 1.2 Cấu hình `application.yml`

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}          # Email Gmail của bạn
    password: ${MAIL_APP_PASSWORD}      # Gmail App Password (xem hướng dẫn bên dưới)
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
        debug: false
```

### 1.3 Lấy Gmail App Password

1. Vào Google Account → Security → 2-Step Verification (bật lên nếu chưa)
2. Vào **App passwords** → Chọn **Mail** → **Other** → Đặt tên → **Generate**
3. Copy 16 ký tự → dán vào `MAIL_APP_PASSWORD`

> **Không dùng mật khẩu Gmail thông thường** — Google đã tắt tính năng này.

---

## Bước 2 — Database Schema & Entity

### 2.1 Tạo bảng

```sql
CREATE TABLE email_logs (
    id            BIGSERIAL PRIMARY KEY,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(500) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    reference_id  VARCHAR(50),         -- PNR code hoặc user ID
    status        VARCHAR(20) DEFAULT 'PENDING',
    attempts      INT DEFAULT 0,
    last_error    TEXT,
    next_retry_at TIMESTAMP,
    sent_at       TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_email_logs_retry ON email_logs(status, next_retry_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_email_logs_reference ON email_logs(reference_id);
```

### 2.2 Entity: EmailLog

```java
@Entity
@Table(name = "email_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String templateName;

    private String referenceId;   // PNR, userId...

    private String status = "PENDING";  // PENDING | SENT | FAILED
    private Integer attempts = 0;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime sentAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 2.3 Repository

```java
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    // Tìm email cần retry
    @Query("SELECT e FROM EmailLog e WHERE e.status = 'PENDING' AND e.nextRetryAt <= :now")
    List<EmailLog> findPendingRetries(@Param("now") LocalDateTime now);

    // Kiểm tra đã gửi reminder cho booking này chưa (BR-06)
    boolean existsByReferenceIdAndTemplateName(String referenceId, String templateName);

    // Đếm số lần đã gửi (giới hạn BR-06: max 5 lần)
    int countByReferenceIdAndTemplateName(String referenceId, String templateName);
}
```

---

## Bước 3 — AsyncConfig

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    // Thread pool riêng cho email — không block HTTP threads (BR-01)
    @Bean("emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);           // 2 thread luôn sẵn sàng
        executor.setMaxPoolSize(5);            // Tối đa 5 thread khi tải cao
        executor.setQueueCapacity(100);        // Queue 100 email
        executor.setThreadNamePrefix("email-");
        // Nếu queue đầy: thread gọi sẽ tự xử lý (không drop)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

## Bước 4 — ThymeleafConfig

> ⚠️ **Spring Boot 3.x:** Không dùng `Java8TimeDialect` — đã tích hợp sẵn, thêm vào sẽ gây lỗi `ClassNotFoundException`.

```java
@Configuration
public class ThymeleafEmailConfig {

    @Bean("emailTemplateEngine")
    public SpringTemplateEngine emailTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(emailTemplateResolver());
        // KHÔNG thêm new Java8TimeDialect() — Spring Boot 3.x đã tích hợp sẵn
        return engine;
    }

    private ClassLoaderTemplateResolver emailTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/emails/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(1);
        resolver.setCacheable(false); // Tắt cache khi dev
        return resolver;
    }
}
```

---

## Bước 5 — EmailService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;  // Spring tự inject bean "emailTemplateEngine"
    private final EmailLogRepository emailLogRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ============ CÁC METHOD GỬI EMAIL ============

    @Async("emailTaskExecutor")
    public void sendBookingConfirmation(Booking booking) {
        Context ctx = new Context();
        ctx.setVariable("pnrCode", booking.getPnrCode());
        ctx.setVariable("flightNumber", getFlightNumber(booking));
        ctx.setVariable("from", getOriginName(booking));
        ctx.setVariable("to", getDestinationName(booking));
        ctx.setVariable("departureTime", getDepartureTime(booking));
        ctx.setVariable("totalPrice", formatCurrency(booking.getTotalPrice()));
        ctx.setVariable("passengers", getPassengerList(booking));

        sendEmail(
            booking.getContactEmail(),
            "✈️ Xác nhận đặt vé - Mã PNR: " + booking.getPnrCode(),
            "booking-confirmed",
            ctx,
            booking.getPnrCode()
        );
    }

    @Async("emailTaskExecutor")
    public void sendBookingCancellation(Booking booking) {
        Context ctx = new Context();
        ctx.setVariable("pnrCode", booking.getPnrCode());
        ctx.setVariable("refundAmount", formatCurrency(booking.getRefundAmount()));
        ctx.setVariable("cancelReason", booking.getCancelReason());

        sendEmail(
            booking.getContactEmail(),
            "❌ Thông báo hủy vé - " + booking.getPnrCode(),
            "booking-cancelled",
            ctx,
            booking.getPnrCode()
        );
    }

    @Async("emailTaskExecutor")
    public void sendFlightDelayNotification(Flight flight, List<Booking> affectedBookings) {
        affectedBookings.forEach(booking -> {
            Context ctx = new Context();
            ctx.setVariable("pnrCode", booking.getPnrCode());
            ctx.setVariable("flightNumber", flight.getFlightNumber());
            ctx.setVariable("newDeparture", flight.getDepartureTime());
            ctx.setVariable("delayMinutes", flight.getDelayMinutes());

            sendEmail(
                booking.getContactEmail(),
                "⚠️ Thông báo chuyến bay trễ - " + flight.getFlightNumber(),
                "flight-delayed",
                ctx,
                booking.getPnrCode()
            );
        });
    }

    @Async("emailTaskExecutor")
    public void sendCheckinReminder(Booking booking) {
        // BR-06: Không gửi quá 5 lần
        if (emailLogRepository.countByReferenceIdAndTemplateName(
                booking.getPnrCode(), "checkin-reminder") >= 5) {
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("pnrCode", booking.getPnrCode());
        ctx.setVariable("flightNumber", getFlightNumber(booking));
        ctx.setVariable("departureTime", getDepartureTime(booking));

        sendEmail(
            booking.getContactEmail(),
            "🔔 Nhắc nhở: Check-in chuyến bay của bạn",
            "checkin-reminder",
            ctx,
            booking.getPnrCode()
        );
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String email, String resetToken) {
        Context ctx = new Context();
        ctx.setVariable("resetLink",
            "http://localhost:3000/reset-password?token=" + resetToken);
        ctx.setVariable("expiryMinutes", 60);

        sendEmail(email, "🔑 Đặt lại mật khẩu FlightEasy", "password-reset", ctx, email);
    }

    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String email, String fullName) {
        Context ctx = new Context();
        ctx.setVariable("fullName", fullName);

        sendEmail(email, "👋 Chào mừng đến FlightEasy!", "welcome", ctx, email);
    }

    // ============ CORE SEND METHOD ============

    public void sendEmail(String to, String subject, String templateName,
                          Context ctx, String referenceId) {
        EmailLog emailLog = EmailLog.builder()
                .recipient(to)
                .subject(subject)
                .templateName(templateName)
                .referenceId(referenceId)
                .status("PENDING")
                .attempts(0)
                .build();
        emailLog = emailLogRepository.save(emailLog);

        doSend(emailLog, ctx, templateName);
    }

    // Gọi khi retry (đã có log record rồi)
    public void retrySend(EmailLog emailLog) {
        Context ctx = new Context();
        doSend(emailLog, ctx, emailLog.getTemplateName());
    }

    private void doSend(EmailLog emailLog, Context ctx, String templateName) {
        try {
            String htmlContent = templateEngine.process("emails/" + templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "FlightEasy ✈️");
            helper.setTo(emailLog.getRecipient());
            helper.setSubject(emailLog.getSubject());
            helper.setText(htmlContent, true);  // true = HTML

            mailSender.send(message);

            emailLog.setStatus("SENT");
            emailLog.setSentAt(LocalDateTime.now());
            emailLog.setAttempts(emailLog.getAttempts() + 1);
            log.info("Email sent to {} (template={})", emailLog.getRecipient(), templateName);

        } catch (Exception e) {
            emailLog.setAttempts(emailLog.getAttempts() + 1);
            emailLog.setLastError(e.getMessage());
            log.error("Failed to send email to {}: {}", emailLog.getRecipient(), e.getMessage());

            if (emailLog.getAttempts() >= 3) {
                // BR-02: Tối đa 3 lần retry → FAILED
                emailLog.setStatus("FAILED");
                log.error("Email permanently failed after 3 attempts: {}", emailLog.getRecipient());
            } else {
                emailLog.setStatus("PENDING");
                emailLog.setNextRetryAt(calculateNextRetry(emailLog.getAttempts()));
            }
        } finally {
            emailLogRepository.save(emailLog);
        }
    }

    private LocalDateTime calculateNextRetry(int attempts) {
        // BR-02: 1 phút, 5 phút, 15 phút
        return switch (attempts) {
            case 1  -> LocalDateTime.now().plusMinutes(1);
            case 2  -> LocalDateTime.now().plusMinutes(5);
            default -> LocalDateTime.now().plusMinutes(15);
        };
    }

    // ============ HELPERS ============

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return String.format("%,.0f ₫", amount);
    }

    private String getFlightNumber(Booking booking) {
        return booking.getSegments().isEmpty() ? "N/A"
            : booking.getSegments().get(0).getFlightClass().getFlight().getFlightNumber();
    }

    private LocalDateTime getDepartureTime(Booking booking) {
        return booking.getSegments().isEmpty() ? null
            : booking.getSegments().get(0).getFlightClass().getFlight().getDepartureTime();
    }

    private String getOriginName(Booking booking) {
        if (booking.getSegments().isEmpty()) return "N/A";
        Airport origin = booking.getSegments().get(0).getFlightClass().getFlight().getOrigin();
        return origin.getCity() + " (" + origin.getIataCode() + ")";
    }

    private String getDestinationName(Booking booking) {
        if (booking.getSegments().isEmpty()) return "N/A";
        Airport dest = booking.getSegments().get(0).getFlightClass().getFlight().getDestination();
        return dest.getCity() + " (" + dest.getIataCode() + ")";
    }

    private List<Map<String, String>> getPassengerList(Booking booking) {
        return booking.getSegments().stream()
            .flatMap(seg -> seg.getPassengers().stream())
            .map(p -> Map.of(
                "fullName", p.getFirstName() + " " + p.getLastName(),
                "seatNumber", p.getSeat() != null ? p.getSeat().getSeatNumber() : "N/A"
            ))
            .toList();
    }
}
```

---

## Bước 6 — Event Listener

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final EmailService emailService;

    // Lắng nghe khi booking được xác nhận (sau thanh toán VNPay)
    @EventListener
    @Async("emailTaskExecutor")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Sending confirmation email for booking {}", event.getBooking().getPnrCode());
        emailService.sendBookingConfirmation(event.getBooking());
    }

    // Lắng nghe khi booking bị hủy
    @EventListener
    @Async("emailTaskExecutor")
    public void onBookingCancelled(BookingCancelledEvent event) {
        emailService.sendBookingCancellation(event.getBooking());
    }

    // Lắng nghe khi chuyến bay bị hủy (từ FlightService)
    @EventListener
    @Async("emailTaskExecutor")
    public void onFlightCancelled(FlightCancelledEvent event) {
        log.info("Flight {} cancelled — sending notifications", event.getFlight().getFlightNumber());
    }
}

// ======================== EVENT CLASSES ========================
// Tạo các file này trong package: com.flighteasy.event

public class BookingConfirmedEvent {
    private final Booking booking;
    public BookingConfirmedEvent(Booking booking) { this.booking = booking; }
    public Booking getBooking() { return booking; }
}

public class BookingCancelledEvent {
    private final Booking booking;
    public BookingCancelledEvent(Booking booking) { this.booking = booking; }
    public Booking getBooking() { return booking; }
}
```

---

## Bước 7 — Retry Scheduler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final EmailLogRepository emailLogRepository;
    private final EmailService emailService;

    // Chạy mỗi 1 phút (BR-02)
    @Scheduled(fixedDelay = 60_000)
    public void retryFailedEmails() {
        List<EmailLog> pending = emailLogRepository.findPendingRetries(LocalDateTime.now());

        if (!pending.isEmpty()) {
            log.info("Retrying {} failed emails", pending.size());
            pending.forEach(emailLog -> {
                try {
                    emailService.retrySend(emailLog);
                } catch (Exception e) {
                    log.error("Retry failed for email {}: {}", emailLog.getId(), e.getMessage());
                }
            });
        }
    }
}
```

---

## Bước 8 — Check-in Reminder Scheduler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckinReminderScheduler {

    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    // BR-04: Gửi lúc 9:00 sáng, múi giờ Việt Nam
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendCheckinReminders() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd   = tomorrowStart.plusDays(1);

        List<Booking> bookings = bookingRepository
            .findConfirmedBookingsForCheckin(tomorrowStart, tomorrowEnd);

        log.info("Sending check-in reminders for {} bookings", bookings.size());
        bookings.forEach(emailService::sendCheckinReminder);
    }
}
```

Thêm query vào `BookingRepository`:

```java
@Query("""
    SELECT DISTINCT b FROM Booking b
    JOIN b.segments s
    JOIN s.flightClass fc
    JOIN fc.flight f
    WHERE b.status = 'CONFIRMED'
      AND f.departureTime >= :start
      AND f.departureTime < :end
""")
List<Booking> findConfirmedBookingsForCheckin(
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end
);
```

---

## Bước 9 — Template HTML (Thymeleaf)

> ⚠️ **Spring Boot 3.x:** Xóa `xmlns:th-java8time` — không cần thiết nữa. `#temporals` vẫn dùng bình thường.

Tạo file tại: `src/main/resources/templates/emails/booking-confirmed.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px; margin:0">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1)">

  <!-- Header -->
  <div style="background:#1a56db; padding:24px; text-align:center">
    <h1 style="color:white; margin:0; font-size:24px">✈️ FlightEasy</h1>
    <p style="color:#bfdbfe; margin:8px 0 0">Xác nhận đặt vé thành công</p>
  </div>

  <!-- PNR Code -->
  <div style="padding:24px; text-align:center; background:#eff6ff; border-bottom:1px solid #dbeafe">
    <p style="margin:0; color:#6b7280; font-size:14px">Mã đặt chỗ của bạn</p>
    <h2 style="font-size:36px; letter-spacing:8px; color:#1a56db; margin:8px 0"
        th:text="${pnrCode}">AB3X9K</h2>
  </div>

  <!-- Flight Info -->
  <div style="padding:24px">
    <h3 style="color:#111827; margin:0 0 16px">Thông tin chuyến bay</h3>
    <table style="width:100%; border-collapse:collapse">
      <tr style="border-bottom:1px solid #f3f4f6">
        <td style="padding:10px 0; color:#6b7280; width:40%">Chuyến bay</td>
        <td style="padding:10px 0; font-weight:bold" th:text="${flightNumber}">VJ123</td>
      </tr>
      <tr style="border-bottom:1px solid #f3f4f6">
        <td style="padding:10px 0; color:#6b7280">Hành trình</td>
        <td style="padding:10px 0; font-weight:bold">
          <span th:text="${from}">Hồ Chí Minh</span>
          → <span th:text="${to}">Hà Nội</span>
        </td>
      </tr>
      <tr style="border-bottom:1px solid #f3f4f6">
        <td style="padding:10px 0; color:#6b7280">Khởi hành</td>
        <td style="padding:10px 0; font-weight:bold"
            th:text="${#temporals.format(departureTime, 'HH:mm - dd/MM/yyyy')}">
          06:00 - 15/06/2025
        </td>
      </tr>
      <tr>
        <td style="padding:10px 0; color:#6b7280">Tổng tiền</td>
        <td style="padding:10px 0; font-weight:bold; color:#059669"
            th:text="${totalPrice}">1,555,000 ₫</td>
      </tr>
    </table>
  </div>

  <!-- Passengers -->
  <div style="padding:0 24px 24px">
    <h3 style="color:#111827; margin:0 0 12px">Danh sách hành khách</h3>
    <div th:each="p : ${passengers}"
         style="padding:10px 12px; background:#f9fafb; margin-bottom:8px; border-radius:6px; border-left:3px solid #1a56db">
      <span th:text="${p.fullName}" style="font-weight:bold">NGUYEN VAN A</span>
      — Ghế: <span th:text="${p.seatNumber}" style="color:#1a56db; font-weight:bold">14A</span>
    </div>
  </div>

  <!-- Footer -->
  <div style="background:#f9fafb; padding:16px 24px; text-align:center; color:#9ca3af; font-size:12px; border-top:1px solid #f3f4f6">
    <p style="margin:0 0 4px">FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
    <p style="margin:0">Hỗ trợ: support@flighteasy.vn | Hotline: 1900-XXXX</p>
  </div>

</div>
</body>
</html>
```

---

## Bước 10 — Test

### 10.1 Dùng MailHog để test local (không cần Gmail thật)

```bash
# Cài MailHog bằng Docker
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Cập nhật `application-local.yml`:

```yaml
spring:
  mail:
    host: localhost
    port: 1025
    username: test
    password: test
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false
```

Mở `http://localhost:8025` để xem email.

### 10.2 Thứ tự test

| # | Hành động | Kết quả mong đợi |
|---|-----------|-----------------|
| 1 | Thanh toán booking thành công | Email `booking-confirmed` được gửi |
| 2 | Hủy booking | Email `booking-cancelled` được gửi |
| 3 | Dừng SMTP server → trigger gửi email | Log status = PENDING, nextRetryAt được set |
| 4 | Khởi động lại SMTP → đợi Scheduler | Email được gửi lại, status = SENT |
| 5 | Gửi thất bại 3 lần | Status = FAILED |

---

## Lưu ý quan trọng

- `@Async` trên method email đảm bảo email không làm chậm API response (BR-01)
- Template file phải nằm đúng đường dẫn: `src/main/resources/templates/emails/`
- `@EnableAsync` phải được đặt trong `AsyncConfig` hoặc main class
- Với production: cân nhắc dùng **SendGrid** hoặc **AWS SES** thay Gmail (giới hạn 500 mail/ngày với Gmail)
- Khi dùng `@Async` + `@EventListener` — Spring sẽ publish event trong transaction gốc rồi gửi email sau

---

## Tóm tắt thay đổi so với bản gốc (Spring Boot 3.x)

| # | Thay đổi | Lý do |
|---|----------|-------|
| 1 | Xóa dependency `thymeleaf-extras-java8time` | Đã tích hợp sẵn trong Thymeleaf 3.1+ |
| 2 | Xóa `engine.addDialect(new Java8TimeDialect())` trong `ThymeleafEmailConfig` | Không còn tồn tại trong Spring Boot 3.x |
| 3 | Xóa `xmlns:th-java8time` trong template HTML | Không cần thiết nữa |
| 4 | Sửa comment dependency (bản gốc 2 cái cùng ghi `JavaMailSender`) | Đúng tên dependency |
