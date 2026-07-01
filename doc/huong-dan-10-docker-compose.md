# Hướng dẫn 10 — Docker & Docker Compose hoàn chỉnh

> **Mục tiêu:** `docker compose up` một lệnh → chạy được toàn bộ app (Spring Boot + PostgreSQL + Redis)  
> **Stack:** Docker · Docker Compose v2 · Multi-stage build

---

## 1. Tại sao cần Docker?

Vấn đề thực tế:
- *"Trên máy mình chạy được, trên máy bạn không chạy"* — environment khác nhau
- Interviewer clone repo, chạy `docker compose up`, 2 phút sau có app demo

**Bạn học được gì:**
- Multi-stage build — giảm image size từ ~600MB xuống ~150MB
- Health check — container không báo "healthy" cho đến khi DB thật sự sẵn sàng
- `.env` file — không hardcode password vào `docker-compose.yml`
- Networking — container giao tiếp với nhau bằng service name, không phải `localhost`

---

## 2. Cấu trúc file cần tạo

```
flighteasy/
├── Dockerfile              ← build image cho Spring Boot app
├── docker-compose.yml      ← orchestrate app + postgres + redis
├── .env.example            ← template env (commit lên git)
├── .env                    ← giá trị thật (KHÔNG commit — thêm vào .gitignore)
└── .dockerignore           ← bỏ qua file không cần khi build
```

---

## 3. Tạo `.dockerignore`

Tạo file `.dockerignore` ở root project:

```
target/
.git/
.idea/
*.md
.env
.env.*
!.env.example
*.log
```

**Tại sao cần?** Không có file này, Docker copy toàn bộ thư mục vào context — bao gồm cả `target/` (hàng trăm MB). Build sẽ chậm hơn nhiều.

---

## 4. Tạo Dockerfile — Multi-stage Build

Tạo file `Dockerfile` ở root project:

```dockerfile
# ─── Stage 1: Build ─────────────────────────────────────────────────────────
# Dùng Maven image để build — image này to nhưng chỉ dùng ở stage này
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml trước để cache dependencies
# Nếu chỉ đổi code Java mà không đổi pom.xml → Docker dùng layer cache,
# không download lại dependency → build nhanh hơn nhiều
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source và build
COPY src ./src
RUN mvn package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
# Chỉ cần JRE (không cần Maven), image nhỏ hơn nhiều
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo non-root user — best practice bảo mật
RUN addgroup -S flighteasy && adduser -S flighteasy -G flighteasy
USER flighteasy

# Copy chỉ file JAR từ stage builder
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# JVM flags tối ưu cho container
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

**Giải thích multi-stage:**
- Stage `builder`: có Maven, build ra file `.jar`
- Stage `runtime`: chỉ có JRE + file `.jar` → image cuối nhỏ (~150MB thay vì ~600MB)

---

## 5. Tạo `.env.example`

File này commit lên Git để người khác biết cần set những biến gì:

```bash
# Database
DB_URL=jdbc:postgresql://postgres:5432/flighteasy
DB_USERNAME=flighteasy
DB_PASSWORD=change_me_in_production

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# JWT
JWT_SECRET=change_me_to_at_least_32_char_random_string_here

# VNPay (sandbox)
VNPAY_TMN_CODE=your_tmn_code
VNPAY_HASH_SECRET=your_hash_secret

# Email (Mailpit local — không cần đổi khi dev)
MAIL_HOST=mailpit
MAIL_PORT=1025
```

Tạo file `.env` (copy từ `.env.example`) và điền giá trị thật. Thêm `.env` vào `.gitignore`.

---

## 6. Tạo `docker-compose.yml`

```yaml
services:

  # ─── PostgreSQL ─────────────────────────────────────────────────────────────
  postgres:
    image: postgres:16-alpine
    container_name: flighteasy_postgres
    environment:
      POSTGRES_DB: flighteasy
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"          # expose để dùng IDE kết nối trực tiếp khi dev
    volumes:
      - postgres_data:/var/lib/postgresql/data   # data persist khi restart
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME} -d flighteasy"]
      interval: 5s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - flighteasy_net
    restart: unless-stopped

  # ─── Redis ──────────────────────────────────────────────────────────────────
  redis:
    image: redis:7-alpine
    container_name: flighteasy_redis
    command: redis-server --appendonly yes    # persistence
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - flighteasy_net
    restart: unless-stopped

  # ─── Mailpit (email catcher khi dev) ────────────────────────────────────────
  mailpit:
    image: axllent/mailpit:latest
    container_name: flighteasy_mailpit
    ports:
      - "8025:8025"    # Web UI — mở http://localhost:8025 để xem email
      - "1025:1025"    # SMTP port
    networks:
      - flighteasy_net
    restart: unless-stopped

  # ─── Spring Boot App ─────────────────────────────────────────────────────────
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: flighteasy_app
    ports:
      - "8080:8080"
    environment:
      # Database — dùng service name "postgres", không phải localhost
      DB_URL: jdbc:postgresql://postgres:5432/flighteasy
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      # Redis
      REDIS_HOST: redis
      REDIS_PORT: 6379
      # JWT
      JWT_SECRET: ${JWT_SECRET}
      # VNPay
      VNPAY_TMN_CODE: ${VNPAY_TMN_CODE}
      VNPAY_HASH_SECRET: ${VNPAY_HASH_SECRET}
      # Email
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: 1025
    depends_on:
      postgres:
        condition: service_healthy    # chờ postgres healthy mới start app
      redis:
        condition: service_healthy    # chờ redis healthy mới start app
    networks:
      - flighteasy_net
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s    # cho app 60s để khởi động trước khi bắt đầu check

volumes:
  postgres_data:
  redis_data:

networks:
  flighteasy_net:
    driver: bridge
```

---

## 7. Cập nhật application.yml để đọc từ env

Mở `src/main/resources/application.yml`, thêm các biến mail:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  data:
    redis:
      host: ${REDIS_HOST:localhost}     # :localhost là default nếu biến không có
      port: ${REDIS_PORT:6379}

  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:1025}
    username: test
    password: test
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
```

---

## 8. Thêm Spring Actuator để health check hoạt động

Thêm vào `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Thêm vào `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never    # không lộ thông tin nhạy cảm ra ngoài
```

---

## 9. Các lệnh Docker thường dùng

```bash
# ─── Build và khởi động toàn bộ
docker compose up --build

# ─── Chạy background (detached)
docker compose up --build -d

# ─── Xem log của từng service
docker compose logs app          # log của Spring Boot
docker compose logs postgres     # log của PostgreSQL
docker compose logs -f app       # theo dõi live (follow)

# ─── Dừng tất cả (giữ data)
docker compose down

# ─── Dừng và XÓA volumes (reset sạch DB)
docker compose down -v

# ─── Rebuild chỉ 1 service
docker compose up --build app

# ─── Vào trong container để debug
docker exec -it flighteasy_postgres psql -U flighteasy
docker exec -it flighteasy_redis redis-cli

# ─── Xem trạng thái health
docker compose ps

# ─── Xem resource usage
docker stats
```

---

## 10. Kiểm tra sau khi chạy

```bash
# 1. Chạy docker compose
docker compose up --build -d

# 2. Theo dõi log app cho đến khi "Started FlighteasyApplication"
docker compose logs -f app

# 3. Test API
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test","email":"t@test.com","password":"pass123"}'
# → {"accessToken":"...","tokenType":"Bearer","user":{...}}

# 4. Xem email đã gửi
# Mở browser: http://localhost:8025
# → Mailpit web UI, xem tất cả email test
```

---

## 11. Vấn đề thường gặp và cách sửa

### App khởi động trước khi PostgreSQL sẵn sàng

**Triệu chứng:** `Connection refused to postgres:5432`  
**Nguyên nhân:** `depends_on` với `condition: service_healthy` đã xử lý, nhưng Flyway/JPA chạy ngay khi Spring context load.  
**Cách sửa:** Đã có health check + `start_period`. Nếu vẫn lỗi, thêm retry trong app:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      connection-timeout: 30000
      initialization-fail-timeout: 60000   # retry kết nối DB trong 60s
```

### Port đã bị chiếm

**Triệu chứng:** `bind: address already in use`  
**Cách sửa:**
```bash
# Xem process đang dùng port 5432
netstat -ano | findstr :5432   # Windows
lsof -i :5432                  # Mac/Linux

# Đổi port mapping trong docker-compose.yml
ports:
  - "5433:5432"   # local:5433 → container:5432
```

### Quên thêm `.env` vào `.gitignore`

```bash
# Thêm vào .gitignore
echo ".env" >> .gitignore

# Nếu đã lỡ commit .env, xóa khỏi git tracking
git rm --cached .env
git commit -m "remove .env from tracking"
```

---

## 12. Câu hỏi phỏng vấn về Docker

**Q: Tại sao dùng multi-stage build?**
> Stage 1 có Maven (~400MB), stage 2 chỉ có JRE (~150MB). Image cuối không chứa Maven và source code — nhỏ hơn, bảo mật hơn (ít attack surface hơn).

**Q: `depends_on` với `condition: service_healthy` khác `depends_on` thường thế nào?**
> `depends_on` thường: chờ container **start** (process chạy). `service_healthy`: chờ health check **pass** (service thật sự sẵn sàng nhận connection). PostgreSQL cần vài giây sau khi process start để chấp nhận kết nối.

**Q: Tại sao không dùng `localhost` trong docker-compose mà dùng service name?**
> Mỗi container là 1 network namespace riêng. `localhost` trong container `app` là chính container đó, không phải host machine hay container `postgres`. Docker Compose tạo DNS nội bộ: service name `postgres` → IP của container postgres.

**Q: Volume trong Docker Compose làm gì?**
> Mount thư mục từ host vào container. `postgres_data:/var/lib/postgresql/data` → data DB được lưu trên host thay vì trong container. Khi `docker compose down` (không có `-v`), data vẫn còn. Không có volume, xóa container là mất hết data.

**Q: `.dockerignore` tương tự `.gitignore` không?**
> Đúng. Nó nói với Docker CLI không copy những file/thư mục đó vào build context khi chạy `docker build`. Không có nó, toàn bộ `target/` (vài trăm MB) được gửi lên Docker daemon mỗi lần build — chậm và tốn bộ nhớ.