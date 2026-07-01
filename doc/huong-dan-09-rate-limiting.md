# Hướng dẫn 09 — Rate Limiting với Bucket4j + Redis

> **Stack:** Bucket4j 8.10 · Redis · Spring Filter  
> **Mục tiêu:** Chặn brute force login và spam API, lưu trạng thái rate limit trên Redis (distributed)

---

## 1. Vấn đề hiện tại

Project đã có `RateLimitService.java` nhưng **bị lỗi** và chỉ lưu state trong RAM:

```java
// LỖI: computeIfAbsent(ip, this::resolveBucket) gọi đệ quy vô hạn
public Bucket resolveBucket(String ip) {
    return buckets.computeIfAbsent(ip, this::resolveBucket);  // ← bug: gọi lại chính nó
}

// LỖI: state lưu ConcurrentHashMap — restart app là mất hết, không share giữa nhiều instance
private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
```

Bài này sẽ sửa lại đúng và chuyển sang **lưu state trên Redis** để hoạt động đúng khi deploy nhiều pod.

---

## 2. Kiến trúc Rate Limiting

```
Request → RateLimitFilter → check Redis (bucket state)
                          ↓
              Còn token? → tiếp tục → Controller
              Hết token? → 429 Too Many Requests
```

**Hai loại rate limit trong project:**

| Loại | Rule | Áp dụng cho |
|---|---|---|
| Login | 5 lần / phút / IP | `POST /api/v1/auth/login` |
| API chung | 60 lần / phút / user | Mọi API có authentication |

---

## 3. Thêm dependency — bucket4j-redis

Mở `pom.xml`, thêm dependency Bucket4j Redis:

```xml
<!-- Bucket4j core đã có, thêm Redis backend -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

---

## 4. Sửa RateLimitService

Mở `src/main/java/com/flighteasy/service/RateLimitService.java` và **thay toàn bộ nội dung**:

```java
package com.flighteasy.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final ProxyManager<byte[]> loginProxyManager;
    private final ProxyManager<byte[]> apiProxyManager;

    // Injected từ Spring — xem cách config bên dưới
    public RateLimitService(ProxyManager<byte[]> loginProxyManager,
                            ProxyManager<byte[]> apiProxyManager) {
        this.loginProxyManager = loginProxyManager;
        this.apiProxyManager = apiProxyManager;
    }

    // Kiểm tra rate limit login: 5 lần / phút / IP
    public boolean isLoginAllowed(String ip) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofMinutes(1))
                        .build())
                .build();

        var bucket = loginProxyManager.builder().build(
                ("rl:login:" + ip).getBytes(),
                () -> config
        );
        return bucket.tryConsume(1);
    }

    // Kiểm tra rate limit API chung: 60 lần / phút / userId hoặc IP
    public boolean isApiAllowed(String key) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(60)
                        .refillIntervally(60, Duration.ofMinutes(1))
                        .build())
                .build();

        var bucket = apiProxyManager.builder().build(
                ("rl:api:" + key).getBytes(),
                () -> config
        );
        return bucket.tryConsume(1);
    }
}
```

---

## 5. Cấu hình ProxyManager trong RedisConfig

Mở `src/main/java/com/flighteasy/config/RedisConfig.java` và **thêm** vào cuối class:

```java
package com.flighteasy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final ObjectMapper objectMapper;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    // Thêm mới: Lettuce client cho Bucket4j
    @Bean
    public StatefulRedisConnection<byte[], byte[]> lettuceByteConnection() {
        RedisClient client = RedisClient.create(
                RedisURI.builder().withHost(redisHost).withPort(redisPort).build()
        );
        return client.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    public ProxyManager<byte[]> loginProxyManager(StatefulRedisConnection<byte[], byte[]> conn) {
        return LettuceBasedProxyManager.builderFor(conn).build();
    }

    @Bean
    public ProxyManager<byte[]> apiProxyManager(StatefulRedisConnection<byte[], byte[]> conn) {
        return LettuceBasedProxyManager.builderFor(conn).build();
    }
}
```

---

## 6. Tạo RateLimitFilter

Tạo file mới `src/main/java/com/flighteasy/filter/RateLimitFilter.java`:

```java
package com.flighteasy.filter;

import com.flighteasy.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)  // Chạy trước JwtAuthFilter
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String ip = getClientIp(request);

        // Rate limit login: 5 lần / phút / IP
        if (path.equals("/api/v1/auth/login") && request.getMethod().equals("POST")) {
            if (!rateLimitService.isLoginAllowed(ip)) {
                log.warn("Rate limit exceeded for login from IP: {}", ip);
                sendTooManyRequests(response, "Quá nhiều lần đăng nhập. Vui lòng thử lại sau 1 phút.");
                return;
            }
        }

        // Tiếp tục filter chain (JWT filter sẽ set Authentication)
        filterChain.doFilter(request, response);

        // Rate limit API chung: 60 lần / phút / user (chạy sau khi JWT đã xử lý)
        // Lưu ý: check sau filterChain không block được request này,
        // nên implement theo cách khác bên dưới (dùng AOP hoặc check trước)
    }

    private String getClientIp(HttpServletRequest request) {
        // Lấy IP thật khi đứng sau proxy/nginx
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"%s\"}", message
        ));
    }
}
```

---

## 7. Đăng ký Filter trong SecurityConfig

Mở `SecurityConfig.java`, thêm filter vào chain. Tìm method `securityFilterChain` và thêm:

```java
// Thêm import
import com.flighteasy.filter.RateLimitFilter;

// Trong method securityFilterChain, trước addFilterBefore của JwtAuthFilter:
.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
```

Ví dụ SecurityConfig đầy đủ đoạn cần thêm:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                JwtAuthFilter jwtAuthFilter,
                                                RateLimitFilter rateLimitFilter) throws Exception {
    return http
            // ... các config hiện tại ...
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)  // ← thêm dòng này
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}
```

---

## 8. Xử lý khi Redis down — Fail Open

Khi Redis không kết nối được, rate limit không nên chặn toàn bộ traffic. Cập nhật `RateLimitService`:

```java
public boolean isLoginAllowed(String ip) {
    try {
        // ... code bucket4j như trên ...
        return bucket.tryConsume(1);
    } catch (Exception e) {
        // Redis down → fail open (cho qua) thay vì chặn hết
        log.error("Rate limit Redis error, failing open: {}", e.getMessage());
        return true;
    }
}
```

---

## 9. Test thủ công bằng Postman

```
# Gọi login 6 lần liên tiếp với cùng 1 IP
POST /api/v1/auth/login
Body: {"email":"test@test.com","password":"wrong"}

# Lần 1-5: 401 Unauthorized (sai password)
# Lần 6:   429 Too Many Requests
#           {"error":"TOO_MANY_REQUESTS","message":"Quá nhiều lần đăng nhập..."}
```

### Kiểm tra key trong Redis:

```bash
# Kết nối redis-cli
docker exec -it redis redis-cli

# Xem key rate limit
KEYS rl:login:*

# Xem TTL của key
TTL rl:login:127.0.0.1

# Xem giá trị (binary, khó đọc — đây là bucket state của Bucket4j)
GET rl:login:127.0.0.1
```

---

## 10. Nâng cao — Rate Limit theo User ID

Sau khi JWT được xác thực (trong JwtAuthFilter), `SecurityContextHolder` có thông tin user. Thêm rate limit per-user bằng cách tạo filter chạy **sau** JwtAuthFilter:

```java
// Trong RateLimitFilter, tách thành method riêng để gọi sau auth
public void checkAuthenticatedUserRateLimit(HttpServletRequest request,
                                             HttpServletResponse response,
                                             FilterChain chain) throws IOException, ServletException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String key = (auth != null && auth.isAuthenticated())
            ? auth.getName()   // dùng email/username
            : getClientIp(request);  // fallback về IP cho guest

    if (!rateLimitService.isApiAllowed(key)) {
        sendTooManyRequests(response, "Quá nhiều request. Thử lại sau 1 phút.");
        return;
    }
    chain.doFilter(request, response);
}
```

---

## 11. Câu hỏi phỏng vấn về Rate Limiting

**Q: Rate limiting là gì? Tại sao cần nó?**
> Giới hạn số request một IP/user có thể gửi trong khoảng thời gian nhất định. Cần để: chặn brute force login, chặn DDoS, bảo vệ DB khỏi bị spam, đảm bảo fair usage.

**Q: Token Bucket algorithm hoạt động thế nào?**
> Mỗi user có 1 "bucket" chứa tối đa N token. Mỗi request tiêu 1 token. Token được nạp lại theo tốc độ cố định (refill rate). Khi hết token → reject request. Ưu điểm: cho phép burst ngắn hạn (dùng hết token dự trữ).

**Q: Tại sao lưu rate limit state trên Redis thay vì ConcurrentHashMap?**
> ConcurrentHashMap chỉ tồn tại trong 1 JVM. Khi deploy 3 pod: mỗi pod đếm riêng → user có thể gửi 3x request mà không bị chặn. Redis là distributed store, 3 pod share cùng 1 counter.

**Q: Fail open vs Fail closed trong rate limiting?**
> Fail open: khi Redis down → cho request qua (ưu tiên availability). Fail closed: khi Redis down → chặn hết (ưu tiên security). Với login API: fail closed an toàn hơn. Với API thông thường: fail open ít ảnh hưởng UX hơn.