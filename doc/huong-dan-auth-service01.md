# Hướng dẫn Implement Auth Service — FlightEasy

> **Module:** Auth Service | **Stack:** Spring Boot + PostgreSQL + JWT  
> **Dựa theo spec:** `01-auth-refresh-token.md` v1.0

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Khởi tạo project & cấu hình |
| 2 | Database Schema & Entity |
| 3 | Repository |
| 4 | JwtService |
| 5 | RefreshTokenService (Rotation + Reuse Detection) |
| 6 | AuthService (Register, Login, Brute Force) |
| 7 | Controller & API |
| 8 | Security Config & JwtAuthFilter |
| 9 | Exception Handling |
| 10 | Test |

---

## Bước 1 — Khởi tạo Project

Vào [start.spring.io](https://start.spring.io) và chọn các dependency:

- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL Driver
- Lombok
- Validation
- Spring Boot DevTools

Thêm thủ công vào `pom.xml`:

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

Cấu hình `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/flighteasy
    username: postgres
    password: yourpassword
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true

app:
  jwt:
    secret: your-256-bit-base64-encoded-secret-key-here
```

---

## Bước 2 — Database Schema & Entity

### 2.1 Tạo bảng DB

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password      VARCHAR(255),
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    avatar_url    TEXT,
    role          VARCHAR(20) DEFAULT 'ROLE_USER',
    provider      VARCHAR(20) DEFAULT 'LOCAL',
    provider_id   VARCHAR(255),
    is_active     BOOLEAN DEFAULT TRUE,
    failed_attempts INT DEFAULT 0,
    locked_until  TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    token         VARCHAR(512) UNIQUE NOT NULL,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_info   VARCHAR(255),
    ip_address    VARCHAR(45),
    is_used       BOOLEAN DEFAULT FALSE,
    expires_at    TIMESTAMP NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE password_reset_tokens (
    id            BIGSERIAL PRIMARY KEY,
    token         VARCHAR(255) UNIQUE NOT NULL,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    is_used       BOOLEAN DEFAULT FALSE,
    expires_at    TIMESTAMP NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_users_email ON users(email);
```

### 2.2 Entity: User

```java
@Entity
@Table(name = "users")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class User implements UserDetails {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;
    private String fullName;
    private String phone;
    private String avatarUrl;

    @Column(name = "role")
    private String role = "ROLE_USER";

    private String provider = "LOCAL";
    private String providerId;
    private Boolean isActive = true;
    private Integer failedAttempts = 0;
    private LocalDateTime lockedUntil;

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;

    // UserDetails
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }
    @Override public boolean isEnabled()                { return isActive; }
    @Override public boolean isAccountNonExpired()      { return true; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
}
```

### 2.3 Entity: RefreshToken

```java
@Entity
@Table(name = "refresh_tokens")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String deviceInfo;
    private String ipAddress;
    private Boolean isUsed = false;
    private LocalDateTime expiresAt;

    @CreationTimestamp private LocalDateTime createdAt;
}
```

### 2.4 Entity: PasswordResetToken

```java
@Entity
@Table(name = "password_reset_tokens")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PasswordResetToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @ManyToOne @JoinColumn(name = "user_id")
    private User user;

    private Boolean isUsed = false;
    private LocalDateTime expiresAt;

    @CreationTimestamp private LocalDateTime createdAt;
}
```

---

## Bước 3 — Repository

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    long countByUserAndIsUsedFalse(User user);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.isUsed = true WHERE r.user = :user")
    void revokeAllByUser(User user);

    @Modifying
    @Query("""
        DELETE FROM RefreshToken r WHERE r.id = (
            SELECT MIN(r2.id) FROM RefreshToken r2
            WHERE r2.user = :user AND r2.isUsed = false
        )
    """)
    void deleteOldestByUser(User user);
}
```

```java
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
}
```

---

## Bước 4 — JwtService

```java
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    private static final long ACCESS_TOKEN_EXPIRE = 15 * 60 * 1000L;      // 15 phút

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

    public boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody();
        return resolver.apply(claims);
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
```

---

## Bước 5 — RefreshTokenService

> ⚠️ Đây là phần quan trọng nhất — chứa logic **Token Rotation** và **Reuse Detection**.

```java
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private static final int MAX_TOKENS_PER_USER = 5;

    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceInfo, String ip) {
        // Giữ tối đa 5 token — xóa cái cũ nhất nếu vượt quá (BR-05)
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
                .expiresAt(LocalDateTime.now().plusDays(7))  // BR-04
                .build();

        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken rotateToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));

        // REUSE DETECTION (BR-08): token đã dùng rồi → có thể bị tấn công!
        if (existing.isUsed()) {
            refreshTokenRepository.revokeAllByUser(existing.getUser());
            throw new TokenReuseException("Phát hiện token bị tái sử dụng. Tất cả phiên đã bị đăng xuất.");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token đã hết hạn");
        }

        // Đánh dấu token cũ là đã dùng (BR-07)
        existing.setUsed(true);
        refreshTokenRepository.save(existing);

        // Tạo token mới (rotation)
        return createRefreshToken(
            existing.getUser(),
            existing.getDeviceInfo(),
            existing.getIpAddress()
        );
    }
}
```

---

## Bước 6 — AuthService

### 6.1 DTO

```java
// Request DTOs
public record RegisterRequest(
    @NotBlank String fullName,
    @Email @NotBlank String email,
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
             message = "Password phải có chữ hoa, chữ thường, số và ít nhất 8 ký tự")
    String password
) {}

public record LoginRequest(String email, String password) {}
public record ForgotPasswordRequest(String email) {}
public record ResetPasswordRequest(String token, String newPassword) {}

// Response DTO
public record AuthResponse(String accessToken, String tokenType, UserInfo user) {
    public record UserInfo(Long id, String email, String fullName, String role) {}
}
```

### 6.2 AuthService

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private static final int MAX_FAILED_ATTEMPTS  = 5;   // BR-09
    private static final int LOCK_DURATION_MINUTES = 30; // BR-09

    // UC-01: Đăng ký
    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletResponse response) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException("Email " + req.email() + " đã được đăng ký");
        }

        User user = User.builder()
                .fullName(req.fullName())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .build();
        userRepository.save(user);

        return buildAuthResponse(user, null, null, response);
    }

    // UC-02: Đăng nhập
    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không đúng"));

        // Kiểm tra tài khoản bị khóa (BR-09)
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Tài khoản bị khóa đến " + user.getLockedUntil());
        }

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
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

        String deviceInfo = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        return buildAuthResponse(user, deviceInfo, ip, response);
    }

    // UC-03: Refresh Token
    @Transactional
    public AuthResponse refresh(String rawToken, HttpServletResponse response) {
        RefreshToken newRefreshToken = refreshTokenService.rotateToken(rawToken);
        String accessToken = jwtService.generateAccessToken(newRefreshToken.getUser());
        setRefreshTokenCookie(response, newRefreshToken.getToken());

        return new AuthResponse(accessToken, "Bearer", null);
    }

    // UC-04: Logout
    @Transactional
    public void logout(String rawToken, HttpServletResponse response) {
        refreshTokenRepository.findByToken(rawToken).ifPresent(t -> {
            t.setUsed(true);
            refreshTokenRepository.save(t);
        });
        clearRefreshTokenCookie(response);
    }

    // UC-05: Logout All
    @Transactional
    public void logoutAll(User user, HttpServletResponse response) {
        refreshTokenRepository.revokeAllByUser(user);
        clearRefreshTokenCookie(response);
    }

    // UC-06: Forgot Password
    @Transactional
    public void forgotPassword(String email) {
        // Luôn thực thi trong cùng khoảng thời gian để chống email enumeration (BR)
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(UUID.randomUUID().toString())
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .isUsed(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);
            emailService.sendResetEmail(user.getEmail(), resetToken.getToken());
        });
    }

    // UC-07: Reset Password
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(req.token())
                .orElseThrow(() -> new InvalidTokenException("Token không hợp lệ"));

        if (resetToken.getIsUsed()) {
            throw new InvalidTokenException("Token đã được sử dụng");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Token đã hết hạn");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        resetToken.setIsUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Thu hồi tất cả refresh token sau khi đổi mật khẩu
        refreshTokenRepository.revokeAllByUser(user);
    }

    // --- Helpers ---

    private AuthResponse buildAuthResponse(User user, String deviceInfo, String ip,
                                           HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ip);
        setRefreshTokenCookie(response, refreshToken.getToken());

        return new AuthResponse(accessToken, "Bearer",
            new AuthResponse.UserInfo(user.getId(), user.getEmail(),
                                      user.getFullName(), user.getRole()));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(true).sameSite("Strict")
                .path("/api/v1/auth").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
```

---

## Bước 7 — Controller

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletResponse response) {
        return ResponseEntity.status(201).body(authService.register(req, response));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(req, request, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refresh_token") String token,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.refresh(token, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "refresh_token") String token,
            HttpServletResponse response) {
        authService.logout(token, response);
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(
            @AuthenticationPrincipal User user,
            HttpServletResponse response) {
        authService.logoutAll(user, response);
        return ResponseEntity.ok(Map.of("message", "Đã đăng xuất khỏi tất cả thiết bị"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        // Luôn trả 200 dù email có tồn tại hay không (chống email enumeration)
        return ResponseEntity.ok(Map.of("message", "Nếu email tồn tại, bạn sẽ nhận được hướng dẫn."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công"));
    }
}
```

---

## Bước 8 — Security Config & JwtAuthFilter

### 8.1 SecurityConfig

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // cost factor 12
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 8.2 JwtAuthFilter

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email = jwtService.extractEmail(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

### 8.3 UserDetailsService

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

---

## Bước 9 — Exception Handling

### 9.1 Custom Exceptions

```java
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String msg) { super(msg); }
}
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String msg) { super(msg); }
}
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String msg) { super(msg); }
}
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String msg) { super(msg); }
}
public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String msg) { super(msg); }
}
public class TokenReuseException extends RuntimeException {
    public TokenReuseException(String msg) { super(msg); }
}
```

### 9.2 GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<?> handleEmailExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(409)
                .body(Map.of("code", "EMAIL_ALREADY_EXISTS", "message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<?> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(401)
                .body(Map.of("code", "INVALID_CREDENTIALS", "message", ex.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<?> handleLocked(AccountLockedException ex) {
        return ResponseEntity.status(423)
                .body(Map.of("code", "ACCOUNT_LOCKED", "message", ex.getMessage()));
    }

    @ExceptionHandler({InvalidTokenException.class, TokenExpiredException.class})
    public ResponseEntity<?> handleInvalidToken(RuntimeException ex) {
        return ResponseEntity.status(401)
                .body(Map.of("code", "INVALID_TOKEN", "message", ex.getMessage()));
    }

    @ExceptionHandler(TokenReuseException.class)
    public ResponseEntity<?> handleTokenReuse(TokenReuseException ex) {
        return ResponseEntity.status(401)
                .body(Map.of("code", "REFRESH_TOKEN_REUSE", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.status(400)
                .body(Map.of("code", "VALIDATION_ERROR", "errors", errors));
    }
}
```

---

## Bước 10 — Test

Thứ tự test bằng Postman / Insomnia:

### Test case bắt buộc

| # | Request | Kết quả mong đợi |
|---|---------|-----------------|
| 1 | `POST /register` với email mới | `201` + `accessToken` + cookie `refresh_token` |
| 2 | `POST /register` với email cũ | `409 EMAIL_ALREADY_EXISTS` |
| 3 | `POST /login` đúng | `200` + `accessToken` + cookie mới |
| 4 | `POST /login` sai password | `401 INVALID_CREDENTIALS` |
| 5 | `POST /login` sai 5 lần | Lần 6 → `423 ACCOUNT_LOCKED` |
| 6 | `POST /refresh` với cookie hợp lệ | `200` + `accessToken` mới + cookie mới |
| 7 | `POST /refresh` với **cookie cũ** (đã dùng 1 lần) | `401 REFRESH_TOKEN_REUSE` + toàn bộ session bị xóa |
| 8 | `POST /logout` | `200` + cookie bị clear |
| 9 | `POST /forgot-password` với email không tồn tại | `200` (không tiết lộ email) |
| 10 | `POST /reset-password` với token hết hạn | `401 INVALID_TOKEN` |

### Cấu hình Postman

Thêm Pre-request Script để tự động attach `Authorization` header:

```javascript
// Đọc access token từ environment variable
const token = pm.environment.get("access_token");
if (token) {
    pm.request.headers.add({
        key: "Authorization",
        value: "Bearer " + token
    });
}
```

Sau khi login/register, lưu token:

```javascript
// Tests tab
const json = pm.response.json();
if (json.accessToken) {
    pm.environment.set("access_token", json.accessToken);
}
```

---

## Security Checklist

- [x] Password hash bằng BCrypt (cost factor 12)
- [x] Refresh Token lưu HttpOnly Cookie (chống XSS)
- [x] Refresh Token Rotation (mỗi lần dùng cấp token mới)
- [x] Reuse Detection (phát hiện token bị đánh cắp → revoke all)
- [x] Brute force protection (khóa sau 5 lần sai, 30 phút)
- [x] Reset password token TTL 1 giờ
- [x] Luôn trả 200 cho forgot-password (chống email enumeration)
- [x] Access Token không lưu thông tin nhạy cảm (chỉ userId, email, role)
- [x] Stateless session (không dùng HttpSession)
- [x] Tối đa 5 Refresh Token active per user (đa thiết bị)
