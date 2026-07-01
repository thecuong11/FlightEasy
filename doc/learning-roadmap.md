# FlightEasy — Learning Roadmap & Nâng Cao

---

## PHẦN 1 — Bạn học được gì từ project này?

### 1.1 Kiến thức cốt lõi (Core)

#### ✅ Spring Security 6 + JWT đúng chuẩn thực tế

Project này không dùng JWT theo kiểu tutorial đơn giản. Bạn học được:

- **Access Token (15 phút)** — stateless, gửi trong `Authorization: Bearer` header
- **Refresh Token (7 ngày)** — lưu `HttpOnly Cookie`, chống XSS
- **Token Blacklist bằng Redis** — revoke token khi logout thay vì chờ hết hạn
- **Token Rotation** — mỗi lần refresh phải đổi refresh token mới (chống reuse attack)
- **Detect Token Reuse** — nếu refresh token cũ bị dùng lại → revoke toàn bộ session của user đó

Đây là pattern được dùng thực tế tại các công ty, khác xa với 90% tutorial trên YouTube.

---

#### ✅ Concurrency — Race Condition khi đặt ghế

Đây là bài toán khó nhất trong project:

- **Pessimistic Lock** (`@Lock(PESSIMISTIC_WRITE)`) — lock row trong DB khi select để ngăn 2 request đặt cùng 1 ghế
- **`@Transactional`** — đảm bảo atomicity khi trừ `available_seats` và tạo booking
- **`SELECT FOR UPDATE`** — hiểu cách database xử lý concurrent write
- **Optimistic Lock** vs **Pessimistic Lock** — khi nào dùng cái nào

Đây là câu hỏi cực kỳ phổ biến trong phỏng vấn Senior level.

---

#### ✅ Redis — Cache + TTL + Blacklist

- **`@Cacheable` / `@CacheEvict`** — cache kết quả search, tự động invalidate khi data thay đổi
- **Cache aside pattern** — query DB → lưu vào Redis → lần sau lấy Redis trước
- **TTL (Time To Live)** — booking session hết hạn sau 15 phút tự động nhờ Redis TTL
- **Blacklist pattern** — lưu token đã revoke, check trước khi xác thực

---

#### ✅ Clean Architecture — Layered Design

```
Controller → Service → Repository → Database
     ↕           ↕
    DTO        Entity
```

- **DTO Pattern** — không trả Entity trực tiếp ra ngoài, tránh lộ data nhạy cảm
- **MapStruct** — generate code compile-time, không dùng reflection như ModelMapper
- **Service Layer** — toàn bộ business logic nằm đây, Controller chỉ route request
- **Repository Pattern** — Spring Data JPA với custom JPQL query
- **Global Exception Handler** — `@RestControllerAdvice` trả error response nhất quán

---

#### ✅ Async + Event-Driven

- **`@Async`** — gửi email không block request chính (user nhận response ngay, email gửi background)
- **Spring Event** (`ApplicationEvent` + `@EventListener`) — tách coupling: khi booking confirmed → publish event → listener gửi email
- **`@Scheduled`** — cron job kiểm tra booking hết hạn mỗi 60 giây, nhắc check-in 24h trước
- **ShedLock** — đảm bảo scheduler chỉ chạy 1 instance khi deploy nhiều pod

---

#### ✅ Payment Integration — VNPay

- **Webhook / IPN (Instant Payment Notification)** — VNPay gọi callback khi thanh toán xong, bạn học cách xác thực chữ ký HMAC-SHA512
- **Idempotency** — callback có thể gọi nhiều lần, phải check trước khi update trạng thái
- **State machine cho Payment** — `PENDING → SUCCESS / FAILED / REFUNDED`
- **Chữ ký số** — tạo và xác minh `checksum` để đảm bảo request không bị giả mạo

---

#### ✅ Database Design thực tế

- **Normalization** — tách bảng đúng chuẩn: Flight, FlightClass, Seat, Booking, BookingSegment
- **Flyway Migration** — version control cho DB schema, không dùng `ddl-auto=update`
- **Audit Fields** — `created_at`, `updated_at`, `created_by` tự động qua `@EntityListeners`
- **Enum trong DB** — `FlightStatus`, `BookingStatus`, `ClassType`
- **PostgreSQL nâng cao** — Row-level locking, Window Functions cho report doanh thu

---

#### ✅ AOP — Aspect-Oriented Programming

- **`@AdminAuditAspect`** — tự động log mọi thao tác của Admin (ai làm gì, lúc mấy giờ) mà không cần viết code log thủ công
- **Cross-cutting concern** — bảo mật, logging, transaction là những thứ AOP giải quyết tốt

---

### 1.2 Những phần QUAN TRỌNG nhất cần nắm chắc

| Độ ưu tiên | Topic | Lý do quan trọng |
|---|---|---|
| ⭐⭐⭐ | JWT + Refresh Token + Rotation | Hỏi 90% phỏng vấn Java backend |
| ⭐⭐⭐ | Pessimistic Lock + `@Transactional` | Phân biệt Senior vs Junior |
| ⭐⭐⭐ | Redis Cache + Invalidation strategy | Hỏi nhiều ở mid/senior level |
| ⭐⭐ | Spring Event + `@Async` | Hiểu async pattern |
| ⭐⭐ | Global Exception Handling | Bắt buộc biết |
| ⭐⭐ | VNPay IPN + HMAC checksum | Thực tế doanh nghiệp |
| ⭐ | Flyway migration | DevOps mindset |
| ⭐ | ShedLock | Distributed system awareness |

---

### 1.3 Câu hỏi phỏng vấn thường gặp

#### Nhóm Spring Boot & Security

**Q: Khác nhau giữa Authentication và Authorization?**
> Authentication = xác minh bạn là ai (JWT token). Authorization = kiểm tra bạn được làm gì (ROLE_ADMIN, ROLE_USER).

**Q: Tại sao dùng Access Token ngắn hạn + Refresh Token dài hạn thay vì 1 token dài hạn?**
> Access Token ngắn hạn giới hạn thiệt hại nếu bị leak (15 phút là vô dụng). Refresh Token dài hạn lưu HttpOnly Cookie — JS không đọc được → chống XSS. Nếu chỉ dùng 1 token dài hạn mà bị đánh cắp → attacker có quyền 7 ngày.

**Q: Token Rotation là gì? Tại sao cần nó?**
> Mỗi lần dùng Refresh Token để lấy Access Token mới, hệ thống phát Refresh Token mới và revoke cái cũ. Nếu token cũ được dùng lại (reuse detection) → có thể token bị đánh cắp → revoke toàn bộ session của user.

**Q: Khác nhau giữa `@Transactional` và không có nó?**
> Không có `@Transactional`: mỗi câu SQL là 1 transaction riêng. Có `@Transactional`: nhiều thao tác DB trong 1 method được wrap trong 1 transaction → nếu lỗi ở bước cuối, toàn bộ rollback. Quan trọng khi: đặt ghế + trừ available_seats phải là atomic.

**Q: `@Transactional(propagation=REQUIRES_NEW)` dùng khi nào?**
> Khi muốn method con chạy trong transaction độc lập với method cha. Ví dụ: ghi audit log — dù transaction cha có rollback thì audit log vẫn được lưu.

---

#### Nhóm Concurrency & Database

**Q: Race condition là gì? Project của bạn xử lý thế nào?**
> Race condition là khi 2 thread đọc data cùng lúc, cùng thấy ghế còn trống, cùng đặt → 2 booking cho 1 ghế. Project dùng `@Lock(PESSIMISTIC_WRITE)` → database lock row khi 1 thread đọc, thread thứ 2 phải đợi → chỉ 1 người đặt thành công.

**Q: Pessimistic Lock vs Optimistic Lock — khi nào dùng cái nào?**
> - **Pessimistic**: collision xảy ra thường xuyên (đặt ghế máy bay giờ cao điểm) → lock trước, tránh retry loop.
> - **Optimistic**: collision hiếm (update profile user) → dùng `@Version`, nếu conflict thì retry, không block thread.

**Q: N+1 Query Problem là gì?**
> Truy vấn 1 list Flight (1 query), rồi với mỗi Flight lại query thêm Airline (N query) → tổng N+1 query. Fix bằng `JOIN FETCH` trong JPQL hoặc `@EntityGraph`.

**Q: Flyway migration tại sao tốt hơn `ddl-auto=update`?**
> `ddl-auto=update` không bao giờ DROP column → schema tích lũy rác theo thời gian, và không thể rollback. Flyway có version, traceable, reviewable, và chạy trên CI/CD an toàn.

---

#### Nhóm Redis & Cache

**Q: Cache invalidation — bạn invalidate cache khi nào?**
> Khi admin update trạng thái chuyến bay hoặc thay đổi giá → dùng `@CacheEvict` để xóa cache kết quả search liên quan. TTL cũng là lưới bảo vệ cuối: cache tự expire sau 5 phút.

**Q: Tại sao không lưu Refresh Token trong database mà dùng Redis?**
> Thực ra project lưu cả hai (DB để audit, Redis cho blacklist nhanh). Redis check nhanh O(1) không cần join bảng. Với hàng triệu token, query DB sẽ chậm hơn.

**Q: Cache stampede là gì?**
> Nhiều request cùng lúc cache miss → tất cả hit DB cùng lúc → DB quá tải. Fix: Probabilistic Early Expiration hoặc mutex lock khi rebuild cache.

---

#### Nhóm Design Pattern & Architecture

**Q: Tại sao cần DTO thay vì trả Entity trực tiếp?**
> 1. Tránh lộ data nhạy cảm (password hash). 2. Tránh vòng lặp vô tận khi serialize quan hệ 2 chiều. 3. API contract độc lập với DB schema — đổi DB không ảnh hưởng API.

**Q: Spring Event được dùng để làm gì trong project?**
> Tách coupling: khi booking được confirm, Service không gọi thẳng EmailService mà publish `BookingConfirmedEvent`. `BookingEventListener` nhận event và gửi email. Nếu mai muốn thêm push notification → chỉ thêm listener, không đụng vào BookingService.

**Q: AOP (Aspect) trong project dùng để làm gì?**
> `AdminAuditAspect` intercept mọi method của Admin controller, tự động ghi log: ai gọi API gì, lúc nào, với payload gì — mà không cần thêm code log vào từng method.

---

#### Nhóm Payment

**Q: VNPay IPN là gì? Tại sao phải verify checksum?**
> IPN (Instant Payment Notification) là callback VNPay gọi sau khi user thanh toán. Checksum là chữ ký HMAC-SHA512 dùng secret key, đảm bảo request đến từ VNPay thật sự, không phải attacker giả mạo để confirm booking miễn phí.

**Q: Idempotency trong payment là gì?**
> VNPay có thể gọi IPN nhiều lần nếu server của bạn không trả về `00` đúng cách. Bạn phải check nếu payment đã ở trạng thái `SUCCESS` thì không xử lý lại — tránh confirm booking 2 lần hoặc gửi email 2 lần.

---

## PHẦN 2 — Tích hợp thêm gì để học nâng cao?

### Tier 1 — Nâng cao ngay (quan trọng cho xin việc)

#### 🔥 Unit Test + Integration Test

**Học được gì:** Testing mindset, Testcontainers, JUnit 5, Mockito

```
- Unit test: Service layer với Mockito (mock Repository)
- Integration test: Controller + Service + DB thật với Testcontainers
- Test coverage: JaCoCo report
- Test VNPay callback: mock HTTP request
```

**Tại sao cần làm ngay:** Không có test = điểm trừ lớn trong phỏng vấn. Hỏi "bạn test code thế nào?" mà trả lời "chạy thử bằng Postman" là thua ngay.

---

#### 🔥 Rate Limiting bằng Redis

**Học được gì:** Sliding window algorithm, Redis INCR + EXPIRE, bảo mật API

```java
// Chặn brute force login: tối đa 5 lần/phút per IP
// Chặn spam tìm kiếm: tối đa 30 request/phút per user
```

Tích hợp vào `JwtAuthFilter` hoặc dùng thư viện Bucket4j.

---

#### 🔥 Docker + Docker Compose hoàn chỉnh

**Học được gì:** Containerization, multi-stage build, networking

```yaml
# docker-compose.yml cần có:
- app (Spring Boot)
- postgres
- redis
- healthcheck cho từng service
- env từ .env file
```

**Mục tiêu:** `docker compose up` → chạy được toàn bộ project trên máy bất kỳ.

---

### Tier 2 — Nâng cao tầm Senior

#### 🚀 Elasticsearch — Tìm kiếm Full-text

**Học được gì:** Inverted index, fuzzy search, relevance scoring

```
- Thay vì LIKE '%SGN%' trong PostgreSQL → Elasticsearch
- Tìm sân bay theo tên không dấu: "Ha Noi" → Hà Nội
- Autocomplete khi gõ tên thành phố
- Tốc độ tìm kiếm nhanh hơn 10-100x với data lớn
```

Dùng Spring Data Elasticsearch hoặc Java client.

---

#### 🚀 Message Queue — RabbitMQ hoặc Kafka

**Học được gì:** Async communication, Producer/Consumer, Dead Letter Queue

```
Thay thế @Async bằng:
Booking confirmed → publish message → Email Consumer xử lý

Lợi ích:
- Nếu email service down → message nằm queue, không mất
- Scale email service độc lập với main app
- Retry tự động khi email gửi thất bại
```

**Gợi ý:** Dùng RabbitMQ (đơn giản hơn), sau đó nâng lên Kafka nếu muốn học streaming.

---

#### 🚀 Microservices — Tách service

**Học được gì:** Service discovery, API Gateway, inter-service communication

```
Tách FlightEasy thành:
- auth-service      (JWT, OAuth2)
- flight-service    (Flight CRUD, Search)
- booking-service   (Booking, Seat)
- payment-service   (VNPay, Stripe)
- notification-service (Email, Push)
- api-gateway       (Spring Cloud Gateway)
```

**Công nghệ học thêm:** Spring Cloud Gateway, Eureka/Consul (Service Discovery), Feign Client, Circuit Breaker (Resilience4j).

**Lưu ý:** Chỉ làm sau khi monolith đã hoàn chỉnh. Microservices sinh ra distributed system problems mới.

---

#### 🚀 WebSocket — Real-time notifications

**Học được gì:** STOMP protocol, bidirectional communication

```
Khi chuyến bay bị delay/cancel:
- Admin update status → push notification real-time đến tất cả user đang xem chuyến bay đó
- Không cần user F5 trang
```

Dùng Spring WebSocket + SockJS + STOMP.

---

#### 🚀 Observability — Prometheus + Grafana

**Học được gì:** Metrics, monitoring, alerting

```
- Prometheus scrape metrics từ Spring Boot Actuator
- Grafana dashboard: số booking/phút, latency API, error rate
- Alert khi error rate > 5%
- Distributed tracing với Zipkin/Jaeger
```

Đây là thứ phân biệt developer biết làm production system với người chỉ biết code.

---

### Tier 3 — Advanced / Chuyên sâu

#### 💡 CQRS — Command Query Responsibility Segregation

**Học được gì:** Tách read model và write model

```
- Write: PostgreSQL (booking, payment → cần ACID)
- Read: Elasticsearch hoặc Redis (search, dashboard → cần tốc độ)
- Event Sourcing: lưu toàn bộ sự kiện thay vì chỉ lưu state hiện tại
```

---

#### 💡 OAuth2 Authorization Server — Tự build

Thay vì chỉ làm OAuth2 Client (login Google), build OAuth2 Server bằng Spring Authorization Server:

```
- Cấp token cho third-party apps
- Quản lý client credentials
- Scope-based authorization
```

---

#### 💡 gRPC — Service-to-Service Communication

Thay HTTP/REST khi giao tiếp giữa microservices nội bộ:

```
- Nhanh hơn REST nhờ binary protocol (Protocol Buffers)
- Strongly typed contract qua .proto file
- Streaming support
```

---

### Bảng tổng hợp

| Technology | Tier | Thời gian học | Giá trị phỏng vấn |
|---|---|---|---|
| Unit Test + Integration Test | 1 | 1 tuần | ⭐⭐⭐ Bắt buộc |
| Rate Limiting (Redis) | 1 | 2-3 ngày | ⭐⭐⭐ Cao |
| Docker Compose hoàn chỉnh | 1 | 2-3 ngày | ⭐⭐⭐ Cao |
| Elasticsearch | 2 | 1-2 tuần | ⭐⭐ Cao ở mid/senior |
| RabbitMQ / Kafka | 2 | 1-2 tuần | ⭐⭐⭐ Rất cao |
| WebSocket real-time | 2 | 3-5 ngày | ⭐⭐ Vừa |
| Prometheus + Grafana | 2 | 3-5 ngày | ⭐⭐ Cao ở senior |
| Microservices | 2-3 | 3-4 tuần | ⭐⭐⭐ Cao nếu làm đúng |
| CQRS + Event Sourcing | 3 | 2-3 tuần | ⭐⭐ Chuyên sâu |
| gRPC | 3 | 1 tuần | ⭐ Niche |

---

### Lộ trình học đề xuất sau khi hoàn thành FlightEasy

```
Tuần 1-2:  Viết test (unit + integration) cho code đã có
Tuần 3:    Rate limiting + hoàn thiện Docker Compose
Tuần 4-5:  Tích hợp RabbitMQ thay @Async cho email
Tuần 6-7:  Thêm Elasticsearch cho flight search
Tuần 8-10: Tách 2-3 service (auth + booking) để học microservices pattern
Tuần 11+:  Prometheus/Grafana, WebSocket
```

---

*Project FlightEasy đã cover 80% kiến thức mà công ty tuyển dụng Java backend yêu cầu. Tier 1 giúp bạn pass technical interview. Tier 2-3 giúp bạn đứng đầu danh sách ứng viên.*