# Service 06 — Email Notification

> **Module:** Notification Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P1 — Quan trọng cho UX, không block core flow

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Hệ thống gửi email tự động cho người dùng tại các sự kiện quan trọng: xác nhận đặt vé, nhắc check-in, thông báo chuyến bay thay đổi. Email được gửi bất đồng bộ (async) để không làm chậm API response.

### 1.2 Danh sách email gửi tự động

| Sự kiện | Trigger | Mô tả |
|---------|---------|-------|
| `BOOKING_CONFIRMED` | Sau khi thanh toán thành công | Xác nhận vé, thông tin chuyến bay, mã PNR |
| `BOOKING_CANCELLED` | User hoặc Admin hủy | Thông báo hủy + số tiền hoàn (nếu có) |
| `CHECKIN_REMINDER` | 24h trước khởi hành | Nhắc check-in online, hành lý quy định |
| `FLIGHT_DELAYED` | Admin cập nhật trạng thái DELAYED | Thông báo giờ mới |
| `FLIGHT_CANCELLED` | Admin hủy chuyến | Thông báo + thông tin hoàn tiền |
| `PAYMENT_FAILED` | VNPay IPN trả về lỗi | Nhắc thử lại, booking sắp hết hạn |
| `PASSWORD_RESET` | User quên mật khẩu | Link reset (TTL 1h) |
| `WELCOME` | Đăng ký thành công | Email chào mừng |

### 1.3 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Email phải gửi bất đồng bộ — không block HTTP response |
| BR-02 | Retry tối đa 3 lần nếu gửi thất bại (delay: 1 phút, 5 phút, 15 phút) |
| BR-03 | Lưu log tất cả email gửi (thành công + thất bại) |
| BR-04 | Checkin reminder gửi lúc 9:00 sáng ngày hôm trước (không gửi 3 giờ sáng) |
| BR-05 | Nội dung email dùng template HTML (Thymeleaf) |
| BR-06 | Không gửi quá 5 email/loại cho cùng 1 booking |
| BR-07 | Email có thể bị delay tối đa 2 phút — acceptable |

---

## 2. Flow Diagram

### 2.1 Flow gửi email sau thanh toán (Event-driven)

```
PaymentService      ApplicationEventPublisher     EmailListener        EmailService       SMTP (Gmail)
      │                       │                        │                    │                   │
      │ [Thanh toán OK]       │                        │                    │                   │
      │ publishEvent(         │                        │                    │                   │
      │  BookingConfirmed     │                        │                    │                   │
      │  Event)               │                        │                    │                   │
      │──────────────────────►│                        │                    │                   │
      │ (return ngay)         │ [Async dispatch]       │                    │                   │
      │                       │───────────────────────►│                    │                   │
      │                       │                        │ onEvent()          │                   │
      │                       │                        │ buildEmailData()   │                   │
      │                       │                        │───────────────────►│                   │
      │                       │                        │                    │ renderTemplate()  │
      │                       │                        │                    │ (Thymeleaf HTML)  │
      │                       │                        │                    │ sendMail()        │
      │                       │                        │                    │──────────────────►│
      │                       │                        │                    │◄──────────────────│
      │                       │                        │                    │ logEmail(SUCCESS) │
```

### 2.2 Flow Retry khi gửi thất bại

```
EmailService          RetryService                DB (email_logs)
     │                     │                           │
     │ sendMail() fails     │                           │
     │────────────────────►│                           │
     │                     │ saveFailedLog()           │
     │                     │──────────────────────────►│
     │                     │ {attempts: 1, nextRetry: +1min}
     │                     │                           │
     │        [1 phút sau]  │                           │
     │ Scheduler retryFailed│                           │
     │────────────────────►│                           │
     │                     │ findPendingRetries()      │
     │                     │──────────────────────────►│
     │                     │ sendMail() again          │
     │                     │ [SUCCESS] → updateLog()   │
     │                     │ [FAILED, attempts < 3]    │
     │                     │ → schedule next retry     │
     │                     │ [FAILED, attempts = 3]    │
     │                     │ → markAsFailed (give up)  │
     │                     │ → alertAdmin()            │
```

### 2.3 Flow Check-in Reminder (Scheduled)

```
Scheduler (8:00 AM daily)    NotificationService         DB              EmailService
          │                          │                    │                    │
          │ sendCheckinReminders()    │                    │                    │
          │─────────────────────────►│                    │                    │
          │                          │ findFlightsTomorrow│                    │
          │                          │───────────────────►│                    │
          │                          │◄───────────────────│                    │
          │                          │ [danh sách booking]│                    │
          │                          │                    │                    │
          │                          │ forEach booking:   │                    │
          │                          │ [chưa gửi reminder?]                   │
          │                          │ sendCheckinEmail()─────────────────────►│
          │                          │ markReminderSent() │                    │
          │                          │───────────────────►│                    │
```

---

## 3. Database Schema

```sql
-- Log email đã gửi
CREATE TABLE email_logs (
    id            BIGSERIAL PRIMARY KEY,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(500) NOT NULL,
    template_name VARCHAR(100) NOT NULL,    -- booking-confirmed, checkin-reminder...
    reference_id  VARCHAR(50),              -- PNR code hoặc user ID liên quan
    status        VARCHAR(20) DEFAULT 'PENDING', -- PENDING|SENT|FAILED
    attempts      INT DEFAULT 0,
    last_error    TEXT,
    next_retry_at TIMESTAMP,
    sent_at       TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_email_logs_status ON email_logs(status, next_retry_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_email_logs_reference ON email_logs(reference_id);
```

---

## 4. API (Internal — không expose ra ngoài)

Email không có REST API. Nó được trigger qua:
- **Spring Events** (`@EventListener`) cho real-time events
- **@Scheduled** cho scheduled notifications (check-in reminder, v.v.)

---

## 5. Code mẫu

### EmailService — Gửi email với Thymeleaf template

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async("emailTaskExecutor") // Chạy trên thread pool riêng
    public void sendBookingConfirmation(Booking booking) {
        String recipient = booking.getContactEmail();
        String subject = "✈️ Xác nhận đặt vé - Mã PNR: " + booking.getPnrCode();

        // Build template context
        Context ctx = new Context();
        ctx.setVariable("pnrCode", booking.getPnrCode());
        ctx.setVariable("passengerName", booking.getPrimaryPassengerName());
        ctx.setVariable("flightNumber", booking.getFlightNumber());
        ctx.setVariable("from", booking.getOriginName());
        ctx.setVariable("to", booking.getDestinationName());
        ctx.setVariable("departureTime", booking.getDepartureTime());
        ctx.setVariable("totalPrice", formatCurrency(booking.getTotalPrice()));
        ctx.setVariable("passengers", booking.getPassengers());

        sendEmail(recipient, subject, "booking-confirmed", ctx, booking.getPnrCode());
    }

    @Async("emailTaskExecutor")
    public void sendFlightDelayNotification(Flight flight, List<Booking> affectedBookings) {
        affectedBookings.forEach(booking -> {
            Context ctx = new Context();
            ctx.setVariable("pnrCode", booking.getPnrCode());
            ctx.setVariable("flightNumber", flight.getFlightNumber());
            ctx.setVariable("originalDeparture", flight.getOriginalDepartureTime());
            ctx.setVariable("newDeparture", flight.getDepartureTime());
            ctx.setVariable("delayMinutes", flight.getDelayMinutes());

            sendEmail(
                booking.getContactEmail(),
                "⚠️ Thông báo thay đổi chuyến bay - " + flight.getFlightNumber(),
                "flight-delayed", ctx, booking.getPnrCode()
            );
        });
    }

    private void sendEmail(String to, String subject, String templateName,
                           Context ctx, String referenceId) {
        EmailLog log = EmailLog.builder()
                .recipient(to)
                .subject(subject)
                .templateName(templateName)
                .referenceId(referenceId)
                .status("PENDING")
                .build();
        log = emailLogRepository.save(log);

        try {
            String htmlContent = templateEngine.process("emails/" + templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "FlightEasy ✈️");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.setStatus("SENT");
            log.setSentAt(LocalDateTime.now());
            log.setAttempts(log.getAttempts() + 1);

        } catch (Exception e) {
            log.setStatus("PENDING");
            log.setAttempts(log.getAttempts() + 1);
            log.setLastError(e.getMessage());
            log.setNextRetryAt(calculateNextRetry(log.getAttempts()));
            this.log.error("Failed to send email to {}: {}", to, e.getMessage());

            if (log.getAttempts() >= 3) {
                log.setStatus("FAILED");
            }
        } finally {
            emailLogRepository.save(log);
        }
    }

    private LocalDateTime calculateNextRetry(int attempts) {
        return switch (attempts) {
            case 1 -> LocalDateTime.now().plusMinutes(1);
            case 2 -> LocalDateTime.now().plusMinutes(5);
            default -> LocalDateTime.now().plusMinutes(15);
        };
    }
}
```

### Thread Pool Config cho Email

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### Check-in Reminder Scheduler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckinReminderScheduler {

    private final FlightRepository flightRepository;
    private final EmailService emailService;

    // Chạy lúc 9:00 sáng mỗi ngày
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendCheckinReminders() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd = tomorrowStart.plusDays(1);

        List<Booking> bookings = bookingRepository
            .findConfirmedBookingsForCheckin(tomorrowStart, tomorrowEnd);

        log.info("Sending check-in reminders for {} bookings", bookings.size());
        bookings.forEach(emailService::sendCheckinReminder);
    }
}
```

### Template email mẫu — `booking-confirmed.html` (Thymeleaf)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:20px">
<div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; overflow:hidden">

  <!-- Header -->
  <div style="background:#1a56db; padding:24px; text-align:center">
    <h1 style="color:white; margin:0">✈️ FlightEasy</h1>
    <p style="color:#bfdbfe; margin:8px 0 0">Xác nhận đặt vé thành công</p>
  </div>

  <!-- PNR Code -->
  <div style="padding:24px; text-align:center; background:#eff6ff">
    <p style="margin:0; color:#6b7280">Mã đặt chỗ của bạn</p>
    <h2 style="font-size:36px; letter-spacing:8px; color:#1a56db; margin:8px 0"
        th:text="${pnrCode}">AB3X9K</h2>
  </div>

  <!-- Flight Info -->
  <div style="padding:24px">
    <h3 style="color:#111827">Thông tin chuyến bay</h3>
    <table style="width:100%; border-collapse:collapse">
      <tr>
        <td style="padding:8px 0; color:#6b7280">Chuyến bay</td>
        <td style="padding:8px 0; font-weight:bold" th:text="${flightNumber}">VJ123</td>
      </tr>
      <tr>
        <td style="padding:8px 0; color:#6b7280">Hành trình</td>
        <td style="padding:8px 0; font-weight:bold">
          <span th:text="${from}">Hồ Chí Minh</span>
          → <span th:text="${to}">Hà Nội</span>
        </td>
      </tr>
      <tr>
        <td style="padding:8px 0; color:#6b7280">Khởi hành</td>
        <td style="padding:8px 0; font-weight:bold"
            th:text="${#temporals.format(departureTime, 'HH:mm - dd/MM/yyyy')}">
          06:00 - 15/06/2025
        </td>
      </tr>
      <tr>
        <td style="padding:8px 0; color:#6b7280">Tổng tiền</td>
        <td style="padding:8px 0; font-weight:bold; color:#059669" th:text="${totalPrice}">
          1,555,000 ₫
        </td>
      </tr>
    </table>
  </div>

  <!-- Passengers -->
  <div style="padding:0 24px 24px">
    <h3 style="color:#111827">Danh sách hành khách</h3>
    <div th:each="p : ${passengers}" style="padding:8px; background:#f9fafb; margin-bottom:8px; border-radius:4px">
      <span th:text="${p.fullName}" style="font-weight:bold">NGUYEN VAN A</span>
      — Ghế: <span th:text="${p.seatNumber}">14A</span>
    </div>
  </div>

  <!-- Footer -->
  <div style="background:#f9fafb; padding:16px; text-align:center; color:#6b7280; font-size:12px">
    <p>FlightEasy - Đặt vé thông minh, bay khắp nơi</p>
    <p>Mọi thắc mắc: support@flighteasy.vn | 1900-xxxx</p>
  </div>
</div>
</body>
</html>
```

### application.yml — Email config

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_APP_PASSWORD}   # Gmail App Password (không phải mật khẩu Gmail)
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
        debug: false
```
