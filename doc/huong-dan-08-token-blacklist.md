# Hướng dẫn Implement Service 08 — Token Blacklist (Thu hồi Access Token)

> **Module:** Auth Service (bổ sung)  
> **Stack:** Spring Boot + Redis  
> **Phụ thuộc:** Service 01 (Auth) + Redis (đã cài ở Service 03)

---

## Vấn đề cần giải quyết

JWT Access Token là **stateless** — server không lưu trạng thái. Khi user logout hoặc
phát hiện Reuse Detection, Refresh Token bị xóa khỏi DB nhưng **Access Token vẫn còn
hạn 15 phút** và vẫn gọi API được bình thường.

```
User logout / Reuse Detection
       ↓
Xóa Refresh Token khỏi DB ✅
       ↓
Access Token vẫn còn hạn 15 phút ⚠️
       ↓
Hacker vẫn gọi API được trong 15 phút đó ❌
```

**Giải pháp:** Lưu Access Token bị thu hồi vào Redis với TTL = thời gian còn lại của
token. Mỗi request đến sẽ check Redis trước khi xử lý.

---

## Tổng quan các bước

| Bước | File cần sửa/tạo | Nội dung |
|------|-----------------|----------|
| 1 | Tạo mới `TokenBlacklistService` | Logic blacklist dùng Redis |
| 2 | Sửa `JwtService` | Thêm method lấy expiration |
| 3 | Sửa `JwtAuthFilter` | Check blacklist mỗi request |
| 4 | Sửa `AuthService` | Blacklist token khi logout / reuse detection |
| 5 | Sửa `RefreshTokenService` | Blacklist khi Reuse Detection |

---

## Bước 1 — Tạo `TokenBlacklistService`

**Tạo file mới:** `src/main/java/com/fighteasy/security/TokenBlacklistService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    @Qualifier("stringRedisTemplate")  // Dùng bean String template, không phải Object
    private final RedisTemplate<String, String> redisTemplate;  // Dùng String vì value chỉ là "revoked"
    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    /**
     * Thêm Access Token vào blacklist
     * TTL = thời gian còn lại của token — tự động xóa khỏi Redis khi token hết hạn
     */
    public void blacklist(String accessToken, long remainingMillis) {
        if (remainingMillis <= 0) return; // Token đã hết hạn rồi, không cần blacklist

        String key = BLACKLIST_PREFIX + accessToken;
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(remainingMillis));
        log.info("Access token blacklisted, TTL={}ms", remainingMillis);
    }

    /**
     * Kiểm tra token có bị blacklist không
     */
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + accessToken));
    }
}
```

---

> **Lưu ý:** `TokenBlacklistService` dùng `RedisTemplate<String, String>` (khác với `RedisTemplate<String, Object>` ở Service 03).  
> Cần thêm bean riêng vào `RedisConfig`:
>
> ```java
> @Bean
> public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory factory) {
>     RedisTemplate<String, String> template = new RedisTemplate<>();
>     template.setConnectionFactory(factory);
>     template.setKeySerializer(new StringRedisSerializer());
>     template.setValueSerializer(new StringRedisSerializer());
>     return template;
> }
> ```

---

## Bước 2 — Sửa `JwtService`

**File:** `src/main/java/com/fighteasy/security/JwtService.java`

Thêm method lấy expiration date và tính thời gian còn lại:

```java
// Thêm vào JwtService

// Thêm vào JwtService — giả sử đã có extractClaim() từ trước
/**
 * Lấy thời điểm hết hạn của token
 * extractClaim() phải đã tồn tại trong JwtService
 */
public Date getExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
}

/**
 * Tính số milliseconds còn lại của token
 * Trả về 0 nếu token đã hết hạn
 */
public long getRemainingMillis(String token) {
    Date expiration = getExpiration(token);
    long remaining = expiration.getTime() - System.currentTimeMillis();
    return Math.max(remaining, 0);
}
```

---

## Bước 3 — Sửa `JwtAuthFilter`

**File:** `src/main/java/com/fighteasy/security/JwtAuthFilter.java`

Thêm `TokenBlacklistService` vào constructor và check blacklist trước khi xử lý token:

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService; // ← THÊM DÒNG NÀY

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

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

        // ← THÊM ĐOẠN NÀY: Check blacklist trước khi làm gì khác
        if (tokenBlacklistService.isBlacklisted(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\": \"Token đã bị thu hồi, vui lòng đăng nhập lại\"}");
            return;
        }

        // Phần còn lại giữ nguyên
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return Arrays.stream(SecurityConfig.WHITE_LIST_API)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
```

---

## Bước 4 — Sửa `AuthService`

**File:** `src/main/java/com/fighteasy/service/AuthService.java`

### 4.1 Thêm dependency

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    // ... các field cũ giữ nguyên ...
    private final TokenBlacklistService tokenBlacklistService; // ← THÊM DÒNG NÀY
    private final JwtService jwtService;                       // đảm bảo đã có
```

### 4.2 Sửa method `logout` — blacklist access token

```java
// UC-04: Logout
@Transactional
public void logout(String rawRefreshToken, String accessToken, HttpServletResponse response) {

    // Blacklist Access Token nếu còn hạn
    if (accessToken != null && !accessToken.isBlank()) {
        try {
            long remainingMillis = jwtService.getRemainingMillis(accessToken);
            tokenBlacklistService.blacklist(accessToken, remainingMillis);
        } catch (Exception e) {
            // Token invalid hoặc đã hết hạn — không cần blacklist
            log.warn("Could not blacklist token: {}", e.getMessage());
        }
    }

    // Xóa Refresh Token khỏi DB (giữ nguyên logic cũ)
    refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(t -> {
        t.setUsed(true);
        refreshTokenRepository.save(t);
    });

    clearRefreshTokenCookie(response);
}
```

### 4.3 Sửa `AuthController` — truyền accessToken vào logout

**File:** `src/main/java/com/fighteasy/controller/AuthController.java`

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(
        @CookieValue(name = "refresh_token") String refreshToken,
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        HttpServletResponse response) {

    // Lấy access token từ Authorization header
    String accessToken = null;
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        accessToken = authHeader.substring(7);
    }

    authService.logout(refreshToken, accessToken, response);
    return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
}
```

---

## Bước 5 — Sửa `RefreshTokenService`

**File:** `src/main/java/com/fighteasy/service/RefreshTokenService.java`

Khi phát hiện **Reuse Detection** (token đã dùng rồi bị dùng lại) — đây là dấu hiệu
token bị đánh cắp → cần blacklist Access Token hiện tại ngay lập tức.

```java
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService; // ← THÊM DÒNG NÀY
    private final JwtService jwtService;                       // ← THÊM DÒNG NÀY
    private static final int MAX_TOKENS_PER_USER = 5;

    @Transactional
    public RefreshToken rotateToken(String rawToken, String accessToken) { // ← thêm param accessToken
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));

        // REUSE DETECTION — token đã dùng rồi → có thể bị tấn công!
        if (existing.getIsUsed()) {
            // Thu hồi toàn bộ refresh token của user
            refreshTokenRepository.revokeAllByUser(existing.getUser());

            // ← THÊM ĐOẠN NÀY: Blacklist Access Token hiện tại ngay lập tức
            if (accessToken != null && !accessToken.isBlank()) {
                long remainingMillis = jwtService.getRemainingMillis(accessToken);
                tokenBlacklistService.blacklist(accessToken, remainingMillis);
            }

            throw new TokenReuseException(
                "Phát hiện token bị tái sử dụng. Tất cả phiên đã bị đăng xuất."
            );
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token đã hết hạn");
        }

        // Đánh dấu token cũ là đã dùng
        existing.setIsUsed(true);
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

### Cập nhật `AuthService.refresh()` — truyền accessToken vào rotateToken

```java
// UC-03: Refresh Token
@Transactional
public AuthResponse refresh(String rawToken, String accessToken, HttpServletResponse response) {
    RefreshToken newRefreshToken = refreshTokenService.rotateToken(rawToken, accessToken); // ← thêm accessToken
    String newAccessToken = jwtService.generateAccessToken(newRefreshToken.getUser());
    setRefreshTokenCookie(response, newRefreshToken.getToken());
    return new AuthResponse(newAccessToken, "Bearer", null);
}
```

Cập nhật `AuthController.refresh()`:

```java
@PostMapping("/refresh")
public ResponseEntity<?> refresh(
        @CookieValue(name = "refresh_token") String refreshToken,
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        HttpServletResponse response) {

    String accessToken = null;
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        accessToken = authHeader.substring(7);
    }

    return ResponseEntity.ok(authService.refresh(refreshToken, accessToken, response));
}
```

---

## Tóm tắt — Các file đã thay đổi

| File | Thay đổi |
|------|---------|
| ✅ Tạo mới `TokenBlacklistService` | Logic blacklist dùng Redis |
| ✅ Sửa `JwtService` | Thêm `getExpiration()`, `getRemainingMillis()` |
| ✅ Sửa `JwtAuthFilter` | Inject `TokenBlacklistService`, check blacklist đầu filter |
| ✅ Sửa `AuthService.logout()` | Blacklist access token + thêm param `accessToken` |
| ✅ Sửa `RefreshTokenService.rotateToken()` | Blacklist khi Reuse Detection + thêm param `accessToken` |
| ✅ Sửa `AuthController` | Truyền `accessToken` từ Authorization header vào service |

---

## Test bằng Postman

| # | Hành động | Kết quả mong đợi |
|---|-----------|-----------------|
| 1 | Login → lấy `accessToken` | `200` + accessToken |
| 2 | Gọi API với `accessToken` | `200` bình thường |
| 3 | `POST /logout` với `accessToken` trong Header | `200` |
| 4 | Gọi lại API với `accessToken` vừa logout | `401 Token đã bị thu hồi` |
| 5 | `POST /refresh` với refresh token cũ đã dùng | `401 REFRESH_TOKEN_REUSE` |
| 6 | Gọi API với `accessToken` cũ sau Reuse Detection | `401 Token đã bị thu hồi` |
| 7 | Đợi 15 phút (token hết hạn) → Redis tự xóa | Key không còn trong Redis |

---

## Lưu ý quan trọng

- Redis tự động xóa key khi TTL hết — không cần cleanup thủ công
- Không blacklist token đã hết hạn (remainingMillis <= 0) — không cần thiết vì JwtAuthFilter đã reject
- Key Redis có prefix `blacklist:token:` để dễ phân biệt với các key khác (cache search, refresh token...)
- Với hệ thống nhiều instance (microservices) — Redis shared đảm bảo blacklist hoạt động trên tất cả instance
