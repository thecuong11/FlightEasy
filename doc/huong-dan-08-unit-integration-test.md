# Hướng dẫn 08 — Unit Test & Integration Test

> **Stack:** JUnit 5 · Mockito · Spring Boot Test · Testcontainers · MockMvc  
> **Mục tiêu:** Viết test cho `BookingService`, `AuthService`, `FlightSearchService`, và `BookingController`

---

## 1. Tại sao phải viết test?

Không có test = điểm trừ lớn trong phỏng vấn. Interviewer hỏi:
- *"Bạn đảm bảo code đúng bằng cách nào?"*
- *"Nếu refactor, bạn biết có gì bị vỡ không?"*
- *"Coverage của project bạn là bao nhiêu?"*

**Unit Test** — test từng class riêng lẻ, mock mọi dependency. Chạy nhanh (<1s/test).  
**Integration Test** — khởi động Spring context + DB thật (Testcontainers). Kiểm tra luồng từ Controller xuống DB.

---

## 2. Thêm dependency vào pom.xml

Mở `pom.xml`, thêm vào block `<dependencies>`:

```xml
<!-- Testcontainers — chạy PostgreSQL thật trong Docker khi test -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>

<!-- AssertJ — viết assertion đọc như tiếng anh -->
<!-- Đã có sẵn trong spring-boot-starter-test, không cần thêm -->
```

> **Lưu ý:** `spring-boot-starter-test` đã bao gồm JUnit 5, Mockito, AssertJ, MockMvc. Chỉ cần thêm Testcontainers.

---

## 3. Unit Test — BookingService

**Mục tiêu:** Test business logic — không cần DB, mock hết repository.

Tạo file `src/test/java/com/flighteasy/service/BookingServiceTest.java`:

```java
package com.flighteasy.service;

import com.flighteasy.dto.CreateBookingRequest;
import com.flighteasy.dto.PassengerRequest;
import com.flighteasy.entity.*;
import com.flighteasy.enums.BookingStatus;
import com.flighteasy.exception.custom.NotEnoughSeatsException;
import com.flighteasy.exception.custom.SeatUnavailableException;
import com.flighteasy.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)  // Khởi động Mockito, không load Spring context
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock FlightClassRepository flightClassRepository;
    @Mock SeatRepository seatRepository;
    @Mock PassengerRepository passengerRepository;
    @Mock BookingSegmentRepository bookingSegmentRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BookingService bookingService;  // Inject tất cả @Mock vào đây

    private FlightClass mockFlightClass;
    private Flight mockFlight;

    @BeforeEach
    void setUp() {
        Airport origin = Airport.builder().iataCode("SGN").build();
        Airport dest = Airport.builder().iataCode("HAN").build();

        mockFlight = Flight.builder()
                .id(1L)
                .flightNumber("VN123")
                .origin(origin)
                .destination(dest)
                .departureTime(LocalDateTime.now().plusDays(3))
                .build();

        mockFlightClass = FlightClass.builder()
                .id(1L)
                .flight(mockFlight)
                .basePrice(BigDecimal.valueOf(500_000))
                .availableSeats(10)
                .build();
    }

    // ─── Happy Path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createBooking: tạo booking thành công với 1 adult, không chọn ghế")
    void createBooking_success() {
        // ARRANGE — chuẩn bị dữ liệu
        CreateBookingRequest request = new CreateBookingRequest(
                1L,                     // flightClassId
                List.of(passengerRequest("Nguyen", "Van A", "012345678")),
                null,                   // selectedSeatIds
                "test@gmail.com",
                "0912345678"
        );

        when(flightClassRepository.findByIdWithLock(1L)).thenReturn(Optional.of(mockFlightClass));
        when(passengerRepository.existsDuplicateOnFlight(anyLong(), anyList())).thenReturn(false);
        when(bookingRepository.existsByPnrCode(anyString())).thenReturn(false);

        Booking savedBooking = Booking.builder()
                .pnrCode("ABC123")
                .status(BookingStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .subtotal(BigDecimal.valueOf(500_000))
                .serviceFee(BigDecimal.valueOf(27_500))
                .totalPrice(BigDecimal.valueOf(527_500))
                .segments(List.of())
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingSegment savedSegment = BookingSegment.builder()
                .id(1L).booking(savedBooking).flightClass(mockFlightClass)
                .passengers(List.of()).build();
        when(bookingSegmentRepository.save(any())).thenReturn(savedSegment);

        // ACT — gọi method cần test
        var response = bookingService.createBooking(request, 1L);

        // ASSERT — kiểm tra kết quả
        assertThat(response.pnrCode()).isEqualTo("ABC123");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.pricing().totalPrice()).isEqualByComparingTo("527500");

        // Verify: flightClass.availableSeats đã bị trừ 1
        verify(flightClassRepository).save(argThat(fc -> fc.getAvailableSeats() == 9));
    }

    // ─── Edge Case: không đủ ghế ─────────────────────────────────────────────

    @Test
    @DisplayName("createBooking: ném NotEnoughSeatsException khi không còn ghế")
    void createBooking_throwsWhenNotEnoughSeats() {
        mockFlightClass.setAvailableSeats(0);  // Hết ghế

        CreateBookingRequest request = new CreateBookingRequest(
                1L,
                List.of(passengerRequest("A", "B", "111")),
                null, "a@b.com", "0911"
        );

        when(flightClassRepository.findByIdWithLock(1L)).thenReturn(Optional.of(mockFlightClass));

        // Kỳ vọng exception được ném
        assertThatThrownBy(() -> bookingService.createBooking(request, 1L))
                .isInstanceOf(NotEnoughSeatsException.class)
                .hasMessageContaining("ghế trống");

        // Verify: không có gì được save khi validation fail
        verify(bookingRepository, never()).save(any());
    }

    // ─── Edge Case: ghế đã bị giữ ─────────────────────────────────────────

    @Test
    @DisplayName("createBooking: ném SeatUnavailableException khi ghế đã bị hold")
    void createBooking_throwsWhenSeatUnavailable() {
        Seat takenSeat = Seat.builder().id(10L).seatNumber("12A").isAvailable(false).build();

        CreateBookingRequest request = new CreateBookingRequest(
                1L,
                List.of(passengerRequest("A", "B", "222")),
                List.of(10L),  // chọn ghế 10
                "a@b.com", "0911"
        );

        when(flightClassRepository.findByIdWithLock(1L)).thenReturn(Optional.of(mockFlightClass));
        when(seatRepository.findAllByIdWithLock(List.of(10L))).thenReturn(List.of(takenSeat));

        assertThatThrownBy(() -> bookingService.createBooking(request, 1L))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining("12A");
    }

    // ─── Test cancelBooking ────────────────────────────────────────────────

    @Test
    @DisplayName("cancelBooking: hoàn 70% khi hủy trước 24h")
    void cancelBooking_refund70PercentBefore24h() {
        Booking booking = buildConfirmedBooking(BigDecimal.valueOf(1_000_000),
                LocalDateTime.now().plusHours(30));  // còn 30h

        when(bookingRepository.findByPnrCode("PNR001")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);

        var result = bookingService.cancelBooking("PNR001", 99L);

        assertThat(result.refundAmount()).isEqualByComparingTo("700000");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelBooking: không hoàn tiền khi hủy trong vòng 24h")
    void cancelBooking_noRefundWithin24h() {
        Booking booking = buildConfirmedBooking(BigDecimal.valueOf(1_000_000),
                LocalDateTime.now().plusHours(10));  // còn 10h — trong 24h

        when(bookingRepository.findByPnrCode("PNR002")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);

        var result = bookingService.cancelBooking("PNR002", 99L);

        assertThat(result.refundAmount()).isEqualByComparingTo("0");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private PassengerRequest passengerRequest(String first, String last, String id) {
        return new PassengerRequest(first, last, LocalDate.of(1990, 1, 1),
                "MALE", "VN", "CCCD", id, null, "ADULT", null, null, null);
    }

    private Booking buildConfirmedBooking(BigDecimal total, LocalDateTime departureTime) {
        Flight flight = Flight.builder().departureTime(departureTime).build();
        FlightClass fc = FlightClass.builder().flight(flight).build();
        BookingSegment segment = BookingSegment.builder().flightClass(fc).passengers(List.of()).build();

        return Booking.builder()
                .pnrCode("PNR001")
                .user(User.builder().id(99L).build())
                .status(BookingStatus.CONFIRMED)
                .totalPrice(total)
                .segments(List.of(segment))
                .build();
    }
}
```

---

## 4. Unit Test — AuthService

Tạo `src/test/java/com/flighteasy/service/AuthServiceTest.java`:

```java
package com.flighteasy.service;

import com.flighteasy.dto.RegisterRequest;
import com.flighteasy.entity.RefreshToken;
import com.flighteasy.entity.User;
import com.flighteasy.exception.custom.EmailAlreadyExistsException;
import com.flighteasy.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserAttemptService userAttemptService;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock EmailService emailService;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks AuthService authService;

    @Test
    @DisplayName("register: tạo user mới thành công")
    void register_success() {
        RegisterRequest req = new RegisterRequest("Nguyen Van A", "a@test.com", "password123");
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        when(userRepository.existsByEmail("a@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        User savedUser = User.builder().id(1L).email("a@test.com").fullName("Nguyen Van A").build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(savedUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenService.createRefreshToken(any(), any(), any())).thenReturn(refreshToken);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token-xyz");

        var response = authService.register(req, httpResponse);

        assertThat(response.accessToken()).isEqualTo("access-token-xyz");
        assertThat(response.user().email()).isEqualTo("a@test.com");

        // Verify password được hash trước khi save
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPassword())));
    }

    @Test
    @DisplayName("register: ném EmailAlreadyExistsException khi email đã tồn tại")
    void register_emailAlreadyExists() {
        RegisterRequest req = new RegisterRequest("B", "existing@test.com", "pass");
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req, httpResponse))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("existing@test.com");

        verify(userRepository, never()).save(any());
    }
}
```

---

## 5. Unit Test — FlightSearchService (Cache Logic)

Tạo `src/test/java/com/flighteasy/service/FlightSearchServiceTest.java`:

```java
package com.flighteasy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flighteasy.dto.*;
import com.flighteasy.enums.ClassType;
import com.flighteasy.enums.SortBy;
import com.flighteasy.exception.custom.InvalidSearchException;
import com.flighteasy.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

    @Mock FlightRepository flightRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock Executor searchExecutor;

    FlightSearchService searchService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        searchService = new FlightSearchService(flightRepository, redisTemplate, objectMapper, searchExecutor);
    }

    @Test
    @DisplayName("search: ném InvalidSearchException khi ngày tìm kiếm là hôm nay hoặc quá khứ")
    void search_invalidDate() {
        FlightSearchRequest request = buildRequest(LocalDate.now());  // hôm nay → invalid

        assertThatThrownBy(() -> searchService.search(request))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessageContaining("ngày mai");
    }

    @Test
    @DisplayName("search: ném InvalidSearchException khi tổng hành khách > 9")
    void search_tooManyPassengers() {
        FlightSearchRequest request = buildRequest(LocalDate.now().plusDays(1));
        request.setAdults(9);
        request.setChildren(1);  // tổng = 10 > 9

        assertThatThrownBy(() -> searchService.search(request))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("search: trả về kết quả từ cache khi cache hit")
    void search_cacheHit() throws Exception {
        FlightSearchRequest request = buildRequest(LocalDate.now().plusDays(1));

        // Giả lập cache có dữ liệu
        String cachedJson = """
                {"meta":{"from":"SGN","to":"HAN","date":"2099-01-15","adults":1,"children":0,"infants":0,"classType":"ECONOMY"},
                 "flights":[],"priceRange":{"min":0,"max":0},"availableFilters":{"airlines":[],"duration":{"min":0,"max":0}}}
                """;
        when(valueOps.get(anyString())).thenReturn(cachedJson);

        var result = searchService.search(request);

        assertThat(result).isNotNull();
        // Verify: repository KHÔNG được gọi vì cache hit
        verify(flightRepository, never()).searchFlights(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("search: query DB và lưu cache khi cache miss")
    void search_cacheMiss_queriesDB() {
        FlightSearchRequest request = buildRequest(LocalDate.now().plusDays(1));

        when(valueOps.get(anyString())).thenReturn(null);  // cache miss
        when(flightRepository.searchFlights(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());  // DB trả rỗng

        var result = searchService.search(request);

        assertThat(result.flights()).isEmpty();
        // Verify: DB được gọi và sau đó lưu cache
        verify(flightRepository).searchFlights(eq("SGN"), eq("HAN"), any(), eq("ECONOMY"), eq(1));
        verify(valueOps).set(anyString(), anyString(), any());
    }

    private FlightSearchRequest buildRequest(LocalDate date) {
        FlightSearchRequest req = new FlightSearchRequest();
        req.setFrom("SGN");
        req.setTo("HAN");
        req.setDepartDate(date);
        req.setAdults(1);
        req.setChildren(0);
        req.setInfants(0);
        req.setClassType(ClassType.ECONOMY);
        req.setSortBy(SortBy.PRICE_ASC);
        req.setPage(0);
        req.setSize(10);
        return req;
    }
}
```

---

## 6. Integration Test — AuthController

Integration test khởi động Spring context thật + PostgreSQL thật (qua Testcontainers).

Tạo `src/test/java/com/flighteasy/controller/AuthControllerIntegrationTest.java`:

```java
package com.flighteasy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    // Testcontainers khởi động PostgreSQL thật trong Docker
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flighteasy_test")
            .withUsername("test")
            .withPassword("test");

    // Override datasource URL để dùng container DB thay vì DB config trong yml
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Dùng embedded Redis hoặc mock cho test — xem phần cấu hình bên dưới
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
    }

    @Autowired MockMvc mockMvc;

    @Test
    void register_success_returns200WithToken() throws Exception {
        String body = """
                {
                    "fullName": "Test User",
                    "email": "newuser@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("newuser@test.com"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String body = """
                {"fullName": "A", "email": "dup@test.com", "password": "pass"}
                """;

        // Lần 1: thành công
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Lần 2: conflict
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        // Đăng ký trước
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName": "B", "email": "b@test.com", "password": "correct"}
                    """))
                .andExpect(status().isOk());

        // Đăng nhập sai mật khẩu
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "b@test.com", "password": "wrong"}
                    """))
                .andExpect(status().isUnauthorized());
    }
}
```

### Cấu hình Redis cho test

Thêm `src/test/resources/application-test.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  # Nếu không có Redis local, dùng embedded:
  # Thêm dependency: com.github.codemonstur:embedded-redis:1.4.3
```

Hoặc dùng Testcontainers cho Redis:

```java
@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

// Trong @DynamicPropertySource:
registry.add("spring.data.redis.host", redis::getHost);
registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
```

---

## 7. Cấu hình JaCoCo — đo Coverage

Thêm vào `pom.xml` trong block `<plugins>`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

Chạy và xem report:
```bash
./mvnw test
# Report HTML tại: target/site/jacoco/index.html
```

---

## 8. Thứ tự học

```
Bước 1: Viết test cho UserAttemptService (đơn giản nhất, ít dependency)
Bước 2: Viết test cho BookingService (nhiều case nhất, học được nhiều nhất)
Bước 3: Viết test cho AuthService (hiểu flow register/login)
Bước 4: Thêm Testcontainers, viết AuthController integration test
Bước 5: Viết BookingController integration test với flow end-to-end
Bước 6: Chạy JaCoCo, đặt mục tiêu 70%+ coverage trên Service layer
```

---

## 9. Câu hỏi phỏng vấn về Testing

**Q: Khác nhau giữa unit test và integration test?**
> Unit: test 1 class, mock dependency, chạy nhanh, không cần DB. Integration: test nhiều layer cùng lúc với DB thật, chậm hơn nhưng tìm được bug mà unit test bỏ sót (như SQL query sai).

**Q: Tại sao dùng Testcontainers thay vì H2 in-memory database?**
> H2 không hỗ trợ hết PostgreSQL syntax (ví dụ: `FOR UPDATE`, JSONB, một số function). Testcontainers chạy PostgreSQL thật nên query hỏng sẽ bị bắt ngay ở test, không đợi deploy production mới biết.

**Q: Mockito `@Mock` vs `@MockBean` khác nhau thế nào?**
> `@Mock` dùng với `@ExtendWith(MockitoExtension.class)` — không load Spring context, nhanh hơn. `@MockBean` dùng với `@SpringBootTest` — load Spring context và replace bean thật bằng mock.