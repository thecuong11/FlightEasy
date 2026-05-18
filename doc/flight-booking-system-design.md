# ✈️ FlightEasy — Hệ thống đặt vé máy bay

> **Mục tiêu:** Project portfolio chuẩn doanh nghiệp, thể hiện được kiến trúc sạch, bảo mật tốt, và tư duy thiết kế hệ thống thực tế.

---

## 1. Tổng quan hệ thống

FlightEasy là một REST API backend cho phép người dùng tìm kiếm chuyến bay, đặt vé, thanh toán và quản lý hành trình. Hệ thống phân chia thành 3 nhóm người dùng chính:

- **Guest** — tìm kiếm chuyến bay, xem giá vé
- **User (đã đăng nhập)** — đặt vé, thanh toán, quản lý booking
- **Admin** — quản lý chuyến bay, sân bay, báo cáo doanh thu

---

## 2. Các module chức năng

### 2.1 Authentication & Authorization
- Đăng ký / đăng nhập bằng email + password
- JWT Access Token (15 phút) + Refresh Token (7 ngày)
- Đăng nhập bằng Google OAuth2
- Phân quyền: `ROLE_GUEST`, `ROLE_USER`, `ROLE_ADMIN`
- Đổi mật khẩu, quên mật khẩu qua email

### 2.2 Quản lý chuyến bay (Flight Management)
- CRUD sân bay (Airport): mã IATA, tên, thành phố, quốc gia
- CRUD hãng bay (Airline): tên, logo, quốc gia
- CRUD chuyến bay (Flight): hãng, điểm đi, điểm đến, giờ khởi hành, giờ đến, trạng thái
- Hạng vé (FlightClass): Economy / Business / First Class — mỗi hạng có giá và số ghế riêng
- Cập nhật trạng thái chuyến bay: `SCHEDULED` → `BOARDING` → `DEPARTED` → `ARRIVED` / `CANCELLED` / `DELAYED`

### 2.3 Tìm kiếm chuyến bay (Flight Search)
- Tìm theo: điểm đi, điểm đến, ngày bay, số hành khách, hạng vé
- Lọc: giá, hãng bay, số điểm dừng, khung giờ
- Sắp xếp: giá tăng dần, thời gian bay ngắn nhất, khởi hành sớm nhất
- Tìm vé khứ hồi (round-trip)

### 2.4 Đặt vé (Booking)
- Tạo booking: chọn chuyến bay + hạng vé + số hành khách
- Nhập thông tin hành khách (Passenger): họ tên, ngày sinh, CCCD/passport, quốc tịch
- Chọn ghế ngồi (Seat Selection): sơ đồ ghế, ghế cửa sổ / lối đi
- Trạng thái booking: `PENDING` → `CONFIRMED` → `CANCELLED` / `REFUNDED`
- Booking tự động hết hạn sau 15 phút nếu chưa thanh toán (dùng Scheduler)
- Mã PNR (Passenger Name Record) tự sinh — unique, 6 ký tự

### 2.5 Thanh toán (Payment)
- Tích hợp cổng thanh toán: VNPay (nội địa) + Stripe (quốc tế)
- Trạng thái thanh toán: `PENDING` → `SUCCESS` / `FAILED` / `REFUNDED`
- Hoàn tiền (refund) khi hủy vé theo chính sách
- Lưu lịch sử giao dịch

### 2.6 Thông báo (Notification)
- Email xác nhận booking (template HTML)
- Email nhắc nhở check-in 24h trước chuyến bay
- Email thông báo chuyến bay bị delay / cancelled
- Push notification (tùy chọn mở rộng)

### 2.7 Quản lý người dùng (User Management)
- Xem / cập nhật profile
- Lịch sử booking
- Danh sách hành khách thường dùng (Saved Passengers)
- Upload ảnh đại diện

### 2.8 Admin Dashboard
- Thống kê: tổng booking, doanh thu theo ngày/tháng/năm
- Quản lý tất cả booking, có thể cancel
- Quản lý chuyến bay, cập nhật trạng thái hàng loạt
- Export báo cáo Excel / CSV

---

## 3. Tech Stack & Lý do chọn

### 3.1 Ngôn ngữ & Framework chính

| Công nghệ | Lý do chọn | Thay vì |
|---|---|---|
| **Java 17** | LTS, stable, typed mạnh, ecosystem lớn | Java 11 (thiếu record, text block) |
| **Spring Boot 3.2** | Convention over configuration, production-ready, cộng đồng lớn nhất | Quarkus, Micronaut (ecosystem nhỏ hơn) |
| **Spring Security 6** | Tích hợp sẵn với Spring Boot, hỗ trợ JWT + OAuth2 native | Implement security thủ công (nguy hiểm) |
| **Spring Data JPA** | Giảm boilerplate SQL, repository pattern sẵn có | MyBatis (cần viết SQL thủ công nhiều hơn) |

**Tại sao Spring Boot mà không dùng framework khác?**

Spring Boot là tiêu chuẩn công nghiệp cho Java backend. Hầu hết công ty Việt Nam tuyển dụng đều yêu cầu Spring Boot. Với portfolio, đây là lựa chọn an toàn nhất để gây ấn tượng với HR và tech lead.

---

### 3.2 Database

| Công nghệ | Lý do chọn | Thay vì |
|---|---|---|
| **PostgreSQL 16** | ACID đầy đủ, hỗ trợ JSON, Full-text search, free & open source | MySQL (thiếu một số tính năng nâng cao) / Oracle (tốn phí) |
| **Redis 7** | In-memory cache cực nhanh, hỗ trợ TTL sẵn — dùng cho session, rate limit, cache kết quả tìm kiếm | Memcached (không hỗ trợ data structures phong phú) |
| **Flyway** | Quản lý version cho database schema, migration tự động khi deploy | Liquibase (phức tạp hơn) / ddl-auto (không kiểm soát được thay đổi) |

**Tại sao PostgreSQL mà không dùng MySQL?**

PostgreSQL hỗ trợ `JSONB` (lưu extra data linh hoạt), `Window Functions` (tính toán thống kê doanh thu phức tạp), và `Row-level locking` tốt hơn — quan trọng khi xử lý đặt ghế đồng thời (concurrency).

**Tại sao cần Redis?**

- Cache kết quả tìm kiếm chuyến bay (query nặng, data ít thay đổi trong 5 phút)
- Lưu Refresh Token blacklist (token bị revoke khi logout)
- Rate limiting (chống spam API)
- Booking session TTL 15 phút

---

### 3.3 Bảo mật

| Công nghệ | Lý do chọn | Thay vì |
|---|---|---|
| **JWT (jjwt 0.12)** | Stateless, phù hợp REST API, dễ scale ngang | Session-based (cần sticky session hoặc shared session store) |
| **BCrypt** | Password hashing với salt tự động, cost factor có thể điều chỉnh | MD5/SHA (đã lỗi thời, không an toàn) |
| **OAuth2 (Google)** | Tiện lợi cho user, giảm rủi ro lộ mật khẩu | Tự build login mạng xã hội (phức tạp) |

**Chiến lược JWT nâng cao trong project này:**

```
Access Token:  15 phút  → lưu trong memory (JavaScript) hoặc header
Refresh Token: 7 ngày   → lưu trong HttpOnly Cookie (chống XSS)
Blacklist:     Redis     → lưu token đã revoke khi logout
```

Đây là pattern được dùng trong thực tế, khác với hầu hết tutorial chỉ dùng 1 token duy nhất.

---

### 3.4 Xử lý bất đồng bộ

| Công nghệ | Lý do chọn | Thay vì |
|---|---|---|
| **Spring @Async + ThreadPool** | Gửi email bất đồng bộ không block request chính | Gửi email đồng bộ (user phải chờ) |
| **Spring @Scheduled** | Cron job kiểm tra booking hết hạn, nhắc check-in | Quartz Scheduler (over-engineering cho scale nhỏ) |
| **RabbitMQ** (optional) | Message queue cho notification scale lớn | Kafka (over-engineering, phù hợp data streaming hơn) |

---

### 3.5 Công cụ hỗ trợ

| Công nghệ | Lý do chọn |
|---|---|
| **Lombok** | Giảm boilerplate (getter/setter/builder/constructor) |
| **MapStruct** | Convert Entity ↔ DTO tự động, nhanh hơn ModelMapper vì compile-time |
| **OpenAPI / Swagger** | Tự generate tài liệu API, team frontend không cần hỏi backend |
| **Spring Validation** | Validate request body declarative bằng annotation (`@NotBlank`, `@Email`...) |
| **Testcontainers** | Chạy PostgreSQL thật trong Docker khi integration test |

**Tại sao MapStruct thay vì ModelMapper?**

ModelMapper dùng reflection ở runtime → chậm và dễ lỗi. MapStruct generate code Java ở compile-time → nhanh hơn 10-50x và lỗi được phát hiện ngay lúc build.

---

### 3.6 DevOps & Deployment

| Công nghệ | Lý do chọn | Thay vì |
|---|---|---|
| **Docker + Docker Compose** | Đóng gói app + DB + Redis trong 1 file, chạy được mọi nơi | Cài trực tiếp trên máy (khó reproduce environment) |
| **GitHub Actions** | CI/CD miễn phí, tích hợp sẵn với GitHub, chạy test tự động | Jenkins (cần server riêng) |
| **Railway / Render** | Deploy miễn phí để demo, không cần cấu hình server | AWS/GCP (tốn phí, phức tạp cho portfolio) |

---

## 4. Kiến trúc hệ thống

```
┌─────────────────────────────────────────────────────────┐
│                      CLIENT (Postman / Frontend)         │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTPS
┌───────────────────────────▼─────────────────────────────┐
│              Spring Boot Application                     │
│                                                          │
│  ┌─────────────┐   ┌──────────────┐   ┌──────────────┐  │
│  │  Controller  │──▶│   Service    │──▶│  Repository  │  │
│  │  (REST API)  │   │ (Business)   │   │  (Data JPA)  │  │
│  └─────────────┘   └──────┬───────┘   └──────┬───────┘  │
│         ▲                 │                   │          │
│         │          ┌──────▼───────┐    ┌──────▼───────┐  │
│  JWT Filter        │    Redis     │    │  PostgreSQL  │  │
│  (Security)        │   (Cache)    │    │  (Database)  │  │
└────────────────────┴─────────────┴────┴──────────────┴──┘
                            │
                ┌───────────▼──────────┐
                │   External Services  │
                │  VNPay / Stripe      │
                │  Google OAuth2       │
                │  SMTP (Email)        │
                └──────────────────────┘
```

### Layered Architecture (Clean)

```
src/main/java/com/example/flighteasy/
├── controller/          ← Nhận HTTP request, trả response
├── service/             ← Business logic
│   └── impl/
├── repository/          ← Tương tác database
├── entity/              ← JPA Entity (ánh xạ với bảng DB)
├── dto/                 ← Data Transfer Object (request/response)
│   ├── request/
│   └── response/
├── mapper/              ← MapStruct: Entity ↔ DTO
├── config/              ← Cấu hình Security, Redis, Swagger...
├── filter/              ← JWT Filter, Rate Limit Filter
├── exception/           ← Global exception handler
├── scheduler/           ← Cron jobs
└── util/                ← Helper classes
```

---

## 5. Database Schema

### Bảng chính

```sql
-- Người dùng
users (id, email, password, full_name, phone, avatar_url, role, provider, created_at)

-- Token
refresh_tokens (id, token, user_id, expires_at, revoked)

-- Sân bay
airports (id, iata_code, name, city, country, timezone, latitude, longitude)

-- Hãng bay
airlines (id, iata_code, name, country, logo_url)

-- Chuyến bay
flights (id, flight_number, airline_id, origin_id, destination_id,
         departure_time, arrival_time, duration_minutes, status)

-- Hạng vé của chuyến bay
flight_classes (id, flight_id, class_type, price, total_seats, available_seats)

-- Sơ đồ ghế
seats (id, flight_id, seat_number, class_type, is_window, is_aisle, is_available)

-- Booking
bookings (id, pnr_code, user_id, status, total_price, expires_at, created_at)

-- Booking item (1 booking có thể nhiều chuyến - khứ hồi)
booking_items (id, booking_id, flight_class_id)

-- Hành khách trong booking
passengers (id, booking_item_id, first_name, last_name, date_of_birth,
            nationality, id_number, seat_id)

-- Thanh toán
payments (id, booking_id, amount, currency, gateway, transaction_id,
          status, gateway_response, created_at)
```

---

## 6. API Endpoints chính

### Authentication
```
POST   /api/v1/auth/register          Đăng ký
POST   /api/v1/auth/login             Đăng nhập → trả access token + set refresh cookie
POST   /api/v1/auth/refresh           Làm mới access token
POST   /api/v1/auth/logout            Revoke refresh token
POST   /api/v1/auth/forgot-password   Gửi email reset password
POST   /api/v1/auth/reset-password    Đặt lại mật khẩu
GET    /api/v1/auth/google            Redirect sang Google OAuth
```

### Chuyến bay
```
GET    /api/v1/flights/search         Tìm kiếm chuyến bay (public)
GET    /api/v1/flights/{id}           Chi tiết chuyến bay
GET    /api/v1/flights/{id}/seats     Sơ đồ ghế

POST   /api/v1/admin/flights          Tạo chuyến bay (ADMIN)
PUT    /api/v1/admin/flights/{id}     Cập nhật chuyến bay (ADMIN)
PATCH  /api/v1/admin/flights/{id}/status  Cập nhật trạng thái (ADMIN)
```

### Booking
```
POST   /api/v1/bookings               Tạo booking (tạm giữ ghế 15 phút)
GET    /api/v1/bookings/{pnr}         Xem chi tiết booking
GET    /api/v1/bookings/my            Lịch sử booking của tôi
DELETE /api/v1/bookings/{pnr}         Hủy booking
```

### Thanh toán
```
POST   /api/v1/payments/vnpay/create  Tạo link thanh toán VNPay
GET    /api/v1/payments/vnpay/return  Callback sau khi thanh toán (VNPay redirect về)
POST   /api/v1/payments/stripe        Thanh toán Stripe
POST   /api/v1/payments/{id}/refund   Hoàn tiền (ADMIN)
```

---

## 7. Những điểm nổi bật cho portfolio

### 7.1 Xử lý Concurrency — Race Condition khi đặt ghế

Khi 2 người cùng đặt 1 ghế, phải đảm bảo chỉ 1 người thành công.

```java
// Dùng Pessimistic Lock để lock record trong transaction
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.id = :id")
Optional<Seat> findByIdWithLock(@Param("id") Long id);
```

### 7.2 Booking tự hết hạn — Scheduled Task

```java
@Scheduled(fixedDelay = 60000) // Chạy mỗi 1 phút
public void cancelExpiredBookings() {
    List<Booking> expired = bookingRepository
        .findByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now());
    expired.forEach(this::cancelAndReleaseSeats);
}
```

### 7.3 Cache kết quả tìm kiếm với Redis

```java
@Cacheable(value = "flight-search", key = "#request.cacheKey()")
public List<FlightResponse> searchFlights(FlightSearchRequest request) {
    // Query DB nặng chỉ chạy khi cache miss
}
```

### 7.4 Global Exception Handler — Response nhất quán

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BookingNotFoundException ex) {
        return ResponseEntity.status(404).body(
            ErrorResponse.of(ex.getMessage(), "BOOKING_NOT_FOUND")
        );
    }
}
```

### 7.5 Audit Log tự động

```java
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    @CreatedBy
    private String createdBy;
}
```

---

## 8. Lộ trình phát triển

| Giai đoạn | Nội dung | Thời gian gợi ý |
|---|---|---|
| **Phase 1** | Auth (JWT + Refresh Token), User profile | 1 tuần |
| **Phase 2** | Airport, Airline, Flight CRUD + Search API | 1 tuần |
| **Phase 3** | Booking flow + Seat selection + Scheduler | 1.5 tuần |
| **Phase 4** | Payment (VNPay) + Email notification | 1 tuần |
| **Phase 5** | Admin API + Redis cache + Exception handling | 1 tuần |
| **Phase 6** | Unit test + Integration test + Docker + Deploy | 1 tuần |

**Tổng: ~6-7 tuần** nếu làm bán thời gian (2-3 giờ/ngày)

---

## 9. Kỹ năng thể hiện được sau project

- ✅ Spring Boot 3 + Spring Security 6
- ✅ JWT Authentication + Refresh Token pattern
- ✅ OAuth2 (Google Login)
- ✅ RESTful API design theo chuẩn
- ✅ JPA / Hibernate + Database design
- ✅ Redis caching strategy
- ✅ Concurrency handling (Pessimistic Lock)
- ✅ Clean Architecture (Controller → Service → Repository)
- ✅ Global Exception Handling
- ✅ DTO pattern + MapStruct
- ✅ Scheduled tasks
- ✅ Payment gateway integration
- ✅ Email service (async)
- ✅ Docker + Docker Compose
- ✅ Unit test + Integration test với Testcontainers
- ✅ Swagger / OpenAPI documentation
- ✅ CI/CD với GitHub Actions

---

*Tài liệu này là thiết kế hệ thống cho project FlightEasy — portfolio Spring Boot.*
