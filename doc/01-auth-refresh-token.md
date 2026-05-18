# Service 01 — Authentication & Refresh Token Flow

> **Module:** Auth Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P0 — Bắt buộc, toàn bộ hệ thống phụ thuộc vào đây

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Người dùng đăng ký / đăng nhập vào hệ thống FlightEasy. Sau khi xác thực thành công, hệ thống cấp 2 loại token:

- **Access Token (JWT):** Tồn tại 15 phút, dùng để xác thực mọi API call. Server không lưu, hoàn toàn stateless.
- **Refresh Token (Opaque):** Tồn tại 7 ngày, dùng để lấy Access Token mới khi hết hạn. Server lưu vào DB, set vào HttpOnly Cookie.

### 1.2 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Email phải là duy nhất trong hệ thống |
| BR-02 | Password tối thiểu 8 ký tự, có chữ hoa, chữ thường, số |
| BR-03 | Access Token hết hạn sau 15 phút |
| BR-04 | Refresh Token hết hạn sau 7 ngày |
| BR-05 | Mỗi user chỉ có tối đa 5 Refresh Token hoạt động (đa thiết bị) |
| BR-06 | Khi logout, Refresh Token bị xóa ngay lập tức |
| BR-07 | Refresh Token chỉ dùng được 1 lần (rotation) — sau khi dùng cấp token mới |
| BR-08 | Phát hiện Refresh Token đã dùng rồi → thu hồi toàn bộ token của user (Reuse Detection) |
| BR-09 | Sai password quá 5 lần → khóa tài khoản 30 phút |

### 1.3 Các trường hợp sử dụng (Use Cases)

| Use Case | Actor | Mô tả |
|----------|-------|-------|
| UC-01 | Guest | Đăng ký tài khoản mới |
| UC-02 | Guest | Đăng nhập bằng email + password |
| UC-03 | User | Làm mới Access Token bằng Refresh Token |
| UC-04 | User | Đăng xuất khỏi thiết bị hiện tại |
| UC-05 | User | Đăng xuất khỏi tất cả thiết bị |
| UC-06 | Guest | Quên mật khẩu — nhận email reset |
| UC-07 | Guest | Đặt lại mật khẩu bằng reset token |

---

## 2. Flow Diagram

### 2.1 Flow Đăng nhập & cấp Token

```
Client                    AuthController           AuthService              DB / Redis
  │                            │                       │                       │
  │  POST /auth/login          │                       │                       │
  │  {email, password}         │                       │                       │
  │──────────────────────────►│                       │                       │
  │                            │  authenticate()       │                       │
  │                            │──────────────────────►│                       │
  │                            │                       │  findByEmail()        │
  │                            │                       │──────────────────────►│
  │                            │                       │◄──────────────────────│
  │                            │                       │  BCrypt.verify()      │
  │                            │                       │  (nếu sai → throw)    │
  │                            │                       │                       │
  │                            │                       │  generateAccessToken() │
  │                            │                       │  generateRefreshToken()│
  │                            │                       │  saveRefreshToken()   │
  │                            │                       │──────────────────────►│
  │                            │◄──────────────────────│                       │
  │                            │  Set-Cookie: refresh_token (HttpOnly)         │
  │◄──────────────────────────│                       │                       │
  │  200 OK                    │                       │                       │
  │  {access_token, user}      │                       │                       │
```

### 2.2 Flow Refresh Token (lấy Access Token mới)

```
Client                    AuthController           JwtService               DB
  │                            │                       │                       │
  │  POST /auth/refresh        │                       │                       │
  │  Cookie: refresh_token=xxx │                       │                       │
  │──────────────────────────►│                       │                       │
  │                            │  refresh(token)       │                       │
  │                            │──────────────────────►│                       │
  │                            │                       │  findToken(xxx)       │
  │                            │                       │──────────────────────►│
  │                            │                       │  [KHÔNG TỒN TẠI?]    │
  │                            │                       │  → 401 Unauthorized   │
  │                            │                       │  [ĐÃ DÙNG RỒI?]      │
  │                            │                       │  → Reuse Detection!   │
  │                            │                       │  → Revoke ALL tokens  │
  │                            │                       │  → 401 Security Alert │
  │                            │                       │  [HỢP LỆ]            │
  │                            │                       │  markUsed(xxx)        │
  │                            │                       │  newRefresh = create()│
  │                            │                       │──────────────────────►│
  │                            │                       │  newAccess = JWT()    │
  │                            │◄──────────────────────│                       │
  │                            │  Set-Cookie: refresh_token=newRefresh         │
  │◄──────────────────────────│                       │                       │
  │  200 OK                    │                       │                       │
  │  {access_token}            │                       │                       │
```

### 2.3 Flow Quên mật khẩu

```
Client             AuthController        AuthService          Email Service       DB
  │                     │                    │                     │               │
  │ POST /auth/         │                    │                     │               │
  │ forgot-password     │                    │                     │               │
  │────────────────────►│                    │                     │               │
  │                     │ sendResetEmail()   │                     │               │
  │                     │───────────────────►│                     │               │
  │                     │                    │ findUser()          │               │
  │                     │                    │────────────────────────────────────►│
  │                     │                    │ generateResetToken()│               │
  │                     │                    │ (UUID, TTL 1 giờ)   │               │
  │                     │                    │ saveToken()         │               │
  │                     │                    │────────────────────────────────────►│
  │                     │                    │ sendEmail(template) │               │
  │                     │                    │────────────────────►│               │
  │◄────────────────────│                    │                     │               │
  │ 200 OK (luôn trả    │                    │                     │               │
  │ 200 dù email có     │                    │                     │               │
  │ tồn tại hay không)  │                    │                     │               │

  [User click link trong email]

  │ POST /auth/reset-password               │                     │               │
  │ {token, newPassword}                    │                     │               │
  │────────────────────►│                    │                     │               │
  │                     │ resetPassword()    │                     │               │
  │                     │───────────────────►│                     │               │
  │                     │                    │ validateToken()     │               │
  │                     │                    │ (hết hạn? đã dùng?)│               │
  │                     │                    │ BCrypt(newPassword) │               │
  │                     │                    │ updatePassword()    │               │
  │                     │                    │ revokeAllTokens()   │               │
  │◄────────────────────│                    │                     │               │
  │ 200 OK              │                    │                     │               │
```

---

## 3. Database Schema

```sql
-- Bảng người dùng
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password      VARCHAR(255),                    -- NULL nếu đăng nhập OAuth
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    avatar_url    TEXT,
    role          VARCHAR(20) DEFAULT 'ROLE_USER', -- ROLE_USER | ROLE_ADMIN
    provider      VARCHAR(20) DEFAULT 'LOCAL',     -- LOCAL | GOOGLE
    provider_id   VARCHAR(255),                    -- Google sub ID
    is_active     BOOLEAN DEFAULT TRUE,
    failed_attempts INT DEFAULT 0,
    locked_until  TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

-- Bảng Refresh Token
CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    token         VARCHAR(512) UNIQUE NOT NULL,    -- UUID random
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_info   VARCHAR(255),                    -- "Chrome/Windows", "iPhone 15"
    ip_address    VARCHAR(45),
    is_used       BOOLEAN DEFAULT FALSE,           -- Rotation: đánh dấu đã dùng
    expires_at    TIMESTAMP NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Bảng Password Reset Token
CREATE TABLE password_reset_tokens (
    id            BIGSERIAL PRIMARY KEY,
    token         VARCHAR(255) UNIQUE NOT NULL,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    is_used       BOOLEAN DEFAULT FALSE,
    expires_at    TIMESTAMP NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Index
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_users_email ON users(email);
```

---

## 4. API Specification

### POST `/api/v1/auth/register`
**Mô tả:** Đăng ký tài khoản mới

**Request Body:**
```json
{
  "fullName": "Nguyễn Văn A",
  "email": "nguyenvana@gmail.com",
  "password": "Password@123"
}
```

**Response 201:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "email": "nguyenvana@gmail.com",
    "fullName": "Nguyễn Văn A",
    "role": "ROLE_USER"
  }
}
```
> Cookie được set: `refresh_token=<opaque>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`

**Response 409 (Email đã tồn tại):**
```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email nguyenvana@gmail.com đã được đăng ký"
}
```

---

### POST `/api/v1/auth/login`
**Request Body:**
```json
{ "email": "nguyenvana@gmail.com", "password": "Password@123" }
```
**Response 200:** _(giống register)_

**Response 401:**
```json
{ "code": "INVALID_CREDENTIALS", "message": "Email hoặc mật khẩu không đúng" }
```

---

### POST `/api/v1/auth/refresh`
**Mô tả:** Lấy Access Token mới — không cần body, đọc cookie tự động

**Response 200:**
```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer" }
```

**Response 401:**
```json
{ "code": "REFRESH_TOKEN_REUSE", "message": "Phát hiện token bị tái sử dụng. Vui lòng đăng nhập lại." }
```

---

### POST `/api/v1/auth/logout`
**Headers:** `Authorization: Bearer <access_token>`  
**Mô tả:** Xóa refresh token hiện tại, clear cookie

**Response 200:**
```json
{ "message": "Đăng xuất thành công" }
```

---

### POST `/api/v1/auth/logout-all`
**Mô tả:** Xóa tất cả refresh token của user (đăng xuất khỏi mọi thiết bị)

---

### POST `/api/v1/auth/forgot-password`
```json
{ "email": "nguyenvana@gmail.com" }
```
**Response 200:** _(luôn trả 200 để tránh email enumeration attack)_

---

### POST `/api/v1/auth/reset-password`
```json
{ "token": "uuid-reset-token", "newPassword": "NewPass@456" }
```

---

## 5. Code mẫu

### JwtService — Tạo và validate token
```java
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    private static final long ACCESS_TOKEN_EXPIRE = 15 * 60 * 1000L;      // 15 phút
    private static final long REFRESH_TOKEN_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7 ngày

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole());
        claims.put("userId", user.getId());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Refresh Token là UUID ngẫu nhiên — không phải JWT
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isExpired(token);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
```

### RefreshTokenService — Rotation + Reuse Detection
```java
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private static final int MAX_TOKENS_PER_USER = 5;

    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceInfo, String ip) {
        // Giữ tối đa 5 token — xóa cái cũ nhất nếu vượt quá
        long count = refreshTokenRepository.countByUserAndIsUsedFalse(user);
        if (count >= MAX_TOKENS_PER_USER) {
            refreshTokenRepository.deleteOldestByUser(user);
        }

        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .deviceInfo(deviceInfo)
                .ipAddress(ip)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken rotateToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));

        // REUSE DETECTION: token đã được dùng rồi → tấn công!
        if (existing.isUsed()) {
            // Thu hồi toàn bộ token của user này
            refreshTokenRepository.revokeAllByUser(existing.getUser());
            throw new TokenReuseException("Phát hiện token bị tái sử dụng. Tất cả phiên đã bị đăng xuất.");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token đã hết hạn");
        }

        // Đánh dấu token cũ là đã dùng
        existing.setUsed(true);
        refreshTokenRepository.save(existing);

        // Tạo token mới (rotation)
        return createRefreshToken(existing.getUser(), existing.getDeviceInfo(), existing.getIpAddress());
    }
}
```

### Brute Force Protection
```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException());

        // Kiểm tra tài khoản bị khóa
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Tài khoản bị khóa đến " + user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // Tăng failed attempts
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            }
            userRepository.save(user);
            throw new InvalidCredentialsException("Email hoặc mật khẩu không đúng");
        }

        // Reset failed attempts khi đăng nhập thành công
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return buildAuthResponse(user, request);
    }
}
```

---

## 6. Security Checklist

- [x] Password hash bằng BCrypt (cost factor 12)
- [x] Refresh Token lưu HttpOnly Cookie (chống XSS)
- [x] Refresh Token Rotation (mỗi lần dùng cấp token mới)
- [x] Reuse Detection (phát hiện token bị đánh cắp)
- [x] Brute force protection (khóa sau 5 lần sai)
- [x] Reset password token TTL 1 giờ
- [x] Luôn trả 200 cho forgot-password (chống email enumeration)
- [x] Access Token không lưu thông tin nhạy cảm (chỉ userId, email, role)
