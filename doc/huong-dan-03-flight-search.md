# Hướng dẫn Implement Service 03 — Flight Search

> **Module:** Search Service | **Stack:** Spring Boot + PostgreSQL + Redis  
> **Dựa theo spec:** `03-flight-search.md` v1.0  
> **Phụ thuộc:** Service 02 (Flight Management) phải hoàn thành trước

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Cài đặt Redis |
| 2 | Jackson Config — expose ObjectMapper bean |
| 3 | Enum |
| 4 | DTO Request |
| 5 | FlightSearchResult — Constructor cho JPQL |
| 6 | DTO Response |
| 7 | Repository — Custom Query |
| 8 | FlightSearchService |
| 9 | Controller |
| 10 | Exception & Test |

---

## Bước 1 — Cài đặt Redis

### 1.1 Thêm dependency `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

### 1.2 Cấu hình `application.yml`

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
```

### 1.3 RedisConfig

**Tạo file mới:** `src/main/java/com/fighteasy/config/RedisConfig.java`

```java
@Configuration
@RequiredArgsConstructor  // inject ObjectMapper bean từ JacksonConfig
public class RedisConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // GenericJackson2JsonRedisSerializer tự động lưu type info ("@class")
        // vào JSON → deserialize về đúng class mà không cần ép kiểu thủ công.
        // Dùng objectMapper đã có JavaTimeModule từ JacksonConfig — nhất quán toàn app.
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    // Bean riêng dùng cho TokenBlacklistService (Service 08)
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

> **Tại sao dùng `GenericJackson2JsonRedisSerializer` thay vì `Jackson2JsonRedisSerializer<Object>`?**
>
> `Jackson2JsonRedisSerializer<Object>` không lưu type info vào JSON. Khi đọc ra,
> Jackson không biết phải deserialize về class nào → trả về `LinkedHashMap` thay vì
> `FlightSearchResponse` → cast exception khi dùng.
>
> `GenericJackson2JsonRedisSerializer` tự ghi thêm field `"@class"` vào JSON khi lưu:
> ```json
> { "@class": "com.fighteasy.dto.FlightSearchResponse", "meta": { ... }, ... }
> ```
> Khi đọc ra, Jackson đọc `"@class"` và deserialize đúng về `FlightSearchResponse` tự động.

---

## Bước 2 — JacksonConfig

**Tạo file mới:** `src/main/java/com/fighteasy/config/JacksonConfig.java`

```java
@Configuration
public class JacksonConfig {

    // Expose ObjectMapper bean — RedisConfig sẽ inject bean này.
    // Dùng chung một ObjectMapper duy nhất → cấu hình nhất quán toàn app.
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
```

> **Tại sao dùng `@Bean ObjectMapper` thay vì `Jackson2ObjectMapperBuilderCustomizer`?**
>
> `Jackson2ObjectMapperBuilderCustomizer` chỉ tùy biến ObjectMapper mà Spring MVC dùng
> cho HTTP response — không tạo ra một bean `ObjectMapper` có thể inject vào chỗ khác.
>
> Nếu `RedisConfig` tự tạo `new ObjectMapper()` riêng thì:
> - ObjectMapper đó **không có** `JavaTimeModule` → phải thêm `@JsonSerialize`/`@JsonDeserialize` trên từng field để bù đắp.
> - Có hai ObjectMapper khác cấu hình nhau trong cùng app → dễ sinh bug khó tìm.
>
> Expose `@Bean ObjectMapper` → mọi nơi inject cùng một instance đã cấu hình đầy đủ.
> `@JsonSerialize`/`@JsonDeserialize` trên entity **không còn cần thiết nữa**.

---

## Bước 3 — Enum

```java
// src/main/java/com/fighteasy/enums/SortBy.java
public enum SortBy {
    PRICE_ASC, PRICE_DESC, DURATION_ASC,
    DEPARTURE_ASC, DEPARTURE_DESC, ARRIVAL_ASC
}

// src/main/java/com/fighteasy/enums/TimeRange.java
public enum TimeRange {
    EARLY_MORNING,  // 00:00 - 05:59
    MORNING,        // 06:00 - 11:59
    AFTERNOON,      // 12:00 - 17:59
    EVENING         // 18:00 - 23:59
}

// src/main/java/com/fighteasy/enums/ClassType.java
public enum ClassType {
    ECONOMY, BUSINESS, FIRST_CLASS
}
```

---

## Bước 4 — DTO Request

```java
// Dùng class thường — không dùng record vì cần default value cho Integer
@Data
public class FlightSearchRequest {

    // Bắt buộc
    @NotBlank
    private String from;

    @NotBlank
    private String to;

    @NotNull
    private LocalDate departDate;

    // Dùng Integer (không dùng int) để tránh lỗi null khi không truyền query param
    private Integer adults   = 1;
    private Integer children = 0;
    private Integer infants  = 0;

    // Tùy chọn
    private LocalDate  returnDate;
    private ClassType  classType      = ClassType.ECONOMY;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> airlines;
    private Integer    maxDuration;
    private TimeRange  departTimeRange;

    // Sort & Paging — dùng Integer tránh lỗi null khi không truyền
    private Integer page   = 0;
    private Integer size   = 20;
    private SortBy  sortBy = SortBy.PRICE_ASC;

    // Helpers — luôn check null trước khi dùng
    public int getAdults()   { return adults   != null ? adults   : 1; }
    public int getChildren() { return children != null ? children : 0; }
    public int getInfants()  { return infants  != null ? infants  : 0; }
    public int getPage()     { return page     != null ? page     : 0; }
    public int getSize()     { return size     != null ? Math.min(size, 50) : 20; }

    public SortBy getSortBy() {
        return sortBy != null ? sortBy : SortBy.PRICE_ASC;
    }

    public ClassType getClassType() {
        return classType != null ? classType : ClassType.ECONOMY;
    }

    // Infant không chiếm ghế
    public int getTotalPassengers() {
        return getAdults() + getChildren();
    }
}
```

---

## Bước 5 — FlightSearchResult

> **Lý do không dùng `@AllArgsConstructor`:** JPQL chỉ truyền 19 tham số (không có `totalPrice` và `tags`).
> `@AllArgsConstructor` sẽ sinh constructor có tất cả field → không khớp → lỗi.
> Phải viết constructor tay 19 tham số.

```java
@Data
public class FlightSearchResult {

    private Long id;
    private String flightNumber;
    private String airlineCode;
    private String airlineName;
    private String airlineLogoUrl;

    private String originIata;
    private String originName;
    private String originCity;
    private String destinationIata;
    private String destinationName;
    private String destinationCity;

    // Không cần @JsonSerialize/@JsonDeserialize —
    // ObjectMapper đã có JavaTimeModule xử lý LocalDateTime tự động.
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    private Integer    durationMinutes;
    private BigDecimal pricePerPerson;
    private BigDecimal totalPrice;       // Không có trong DB — tính sau trong service
    private Integer    availableSeats;
    private Integer    baggageAllowanceKg;
    private Boolean    isRefundable;
    private String     status;
    private List<String> tags = new ArrayList<>();

    // Constructor 19 tham số cho JPQL — KHÔNG có totalPrice và tags
    public FlightSearchResult(
            Long id, String flightNumber,
            String airlineCode, String airlineName, String airlineLogoUrl,
            String originIata, String originName, String originCity,
            String destinationIata, String destinationName, String destinationCity,
            LocalDateTime departureTime, LocalDateTime arrivalTime,
            Integer durationMinutes, BigDecimal pricePerPerson,
            Integer availableSeats, Integer baggageAllowanceKg,
            Boolean isRefundable, String status) {
        this.id               = id;
        this.flightNumber     = flightNumber;
        this.airlineCode      = airlineCode;
        this.airlineName      = airlineName;
        this.airlineLogoUrl   = airlineLogoUrl;
        this.originIata       = originIata;
        this.originName       = originName;
        this.originCity       = originCity;
        this.destinationIata  = destinationIata;
        this.destinationName  = destinationName;
        this.destinationCity  = destinationCity;
        this.departureTime    = departureTime;
        this.arrivalTime      = arrivalTime;
        this.durationMinutes  = durationMinutes;
        this.pricePerPerson   = pricePerPerson;
        this.availableSeats   = availableSeats;
        this.baggageAllowanceKg = baggageAllowanceKg;
        this.isRefundable     = isRefundable;
        this.status           = status;
        this.tags             = new ArrayList<>();
    }

    // No-args constructor cho Jackson deserialize từ Redis
    public FlightSearchResult() {
        this.tags = new ArrayList<>();
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }
}
```

---

## Bước 6 — DTO Response

```java
public record FlightSearchResponse(
    SearchMeta meta,
    List<FlightSearchResult> flights,
    PriceRange priceRange,
    AvailableFilters availableFilters
) {
    // Không cần @JsonSerialize/@JsonDeserialize —
    // ObjectMapper bean đã có JavaTimeModule xử lý LocalDate tự động.
    public record SearchMeta(
        String from,
        String to,
        LocalDate departDate,
        int adults,
        int children,
        int infants,
        String classType
    ) {}

    public record PriceRange(BigDecimal min, BigDecimal max) {}

    public record AvailableFilters(
        List<String> airlines,
        DurationRange durationRange
    ) {}

    public record DurationRange(int min, int max) {}
}

public record RoundTripSearchResponse(
    FlightSearchResponse outbound,
    FlightSearchResponse returnTrip,
    FlightPair cheapestCombination
) {
    public record FlightPair(
        FlightSearchResult outboundFlight,
        FlightSearchResult returnFlight,
        BigDecimal totalCombinedPrice
    ) {}
}
```

---

## Bước 7 — Repository Custom Query

```java
public interface FlightRepository extends JpaRepository<Flight, Long> {

    // Sửa package path cho đúng với project: com.fighteasy.dto.FlightSearchResult
    @Query("""
        SELECT new com.fighteasy.dto.FlightSearchResult(
            f.id, f.flightNumber,
            al.iataCode, al.name, al.logoUrl,
            ao.iataCode, ao.name, ao.city,
            ad.iataCode, ad.name, ad.city,
            f.departureTime, f.arrivalTime, f.durationMinutes,
            fc.basePrice, fc.availableSeats, fc.baggageAllowanceKg,
            fc.isRefundable, CAST(f.status AS string)
        )
        FROM Flight f
        JOIN f.airline al
        JOIN f.origin ao
        JOIN f.destination ad
        JOIN f.flightClasses fc
        WHERE ao.iataCode = :originIata
          AND ad.iataCode = :destIata
          AND CAST(f.departureTime AS LocalDate) = :departureDate
          AND f.status IN ('SCHEDULED', 'DELAYED')
          AND fc.classType = :classType
          AND fc.availableSeats >= :passengerCount
    """)
    List<FlightSearchResult> searchFlights(
        @Param("originIata") String originIata,
        @Param("destIata") String destIata,
        @Param("departureDate") LocalDate departureDate,
        @Param("classType") String classType,
        @Param("passengerCount") int passengerCount
    );

    // Dùng cho Admin Dashboard (Service 07)
    @Query("SELECT COUNT(f) FROM Flight f WHERE CAST(f.departureTime AS LocalDate) = :date")
    long countByDepartureDate(@Param("date") LocalDate date);

    @Query("SELECT COUNT(f) FROM Flight f WHERE f.status = :status AND CAST(f.departureTime AS LocalDate) = :date")
    long countByStatusAndDepartureDate(
        @Param("status") FlightStatus status,
        @Param("date") LocalDate date
    );
}
```

---

## Bước 8 — FlightSearchService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightSearchService {

    private final FlightRepository flightRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ======================== ONE-WAY SEARCH ========================

    @SuppressWarnings("unchecked")
    public FlightSearchResponse search(FlightSearchRequest request) {

        // BR-01: Ngày tìm kiếm phải từ ngày mai
        if (!request.getDepartDate().isAfter(LocalDate.now())) {
            throw new InvalidSearchException("Ngày tìm kiếm phải từ ngày mai trở đi");
        }

        // BR-02: Tổng hành khách không vượt quá 9
        if (request.getAdults() + request.getChildren() + request.getInfants() > 9) {
            throw new InvalidSearchException("Tổng hành khách không được vượt quá 9");
        }

        String cacheKey = buildCacheKey(request);

        // Thử lấy từ Redis cache
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT: {}", cacheKey);
            return (FlightSearchResponse) cached;
        }

        log.debug("Cache MISS — query DB: {}", cacheKey);

        // Query DB
        List<FlightSearchResult> results = flightRepository.searchFlights(
            request.getFrom().toUpperCase(),
            request.getTo().toUpperCase(),
            request.getDepartDate(),
            request.getClassType().name(),
            request.getTotalPassengers()
        );

        // Apply filter động
        results = applyFilters(results, request);

        // Sort
        results = applySorting(results, request.getSortBy());

        // Tính tổng giá theo số hành khách
        results.forEach(r -> calculateTotalPrice(r, request));

        // Gắn tag CHEAPEST, FASTEST
        tagFlights(results);

        // Build response với paging
        FlightSearchResponse response = buildResponse(results, request);

        // Cache 5 phút
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);

        return response;
    }

    // ======================== ROUND-TRIP SEARCH ========================

    public RoundTripSearchResponse searchRoundTrip(FlightSearchRequest request) {
        if (request.getReturnDate() == null) {
            throw new InvalidSearchException("Vui lòng cung cấp ngày về cho chuyến khứ hồi");
        }

        // Tạo request cho chuyến về — đảo from/to, dùng returnDate
        FlightSearchRequest returnRequest = new FlightSearchRequest();
        returnRequest.setFrom(request.getTo());
        returnRequest.setTo(request.getFrom());
        returnRequest.setDepartDate(request.getReturnDate());
        returnRequest.setAdults(request.getAdults());
        returnRequest.setChildren(request.getChildren());
        returnRequest.setInfants(request.getInfants());
        returnRequest.setClassType(request.getClassType());
        returnRequest.setSortBy(request.getSortBy());

        // Tìm 2 chiều song song — giảm latency từ 2x xuống ~1x
        CompletableFuture<FlightSearchResponse> outboundFuture =
            CompletableFuture.supplyAsync(() -> search(request));
        CompletableFuture<FlightSearchResponse> returnFuture =
            CompletableFuture.supplyAsync(() -> search(returnRequest));

        CompletableFuture.allOf(outboundFuture, returnFuture).join();

        FlightSearchResponse outbound   = outboundFuture.join();
        FlightSearchResponse returnTrip = returnFuture.join();

        RoundTripSearchResponse.FlightPair cheapest =
            findCheapestPair(outbound.flights(), returnTrip.flights());

        return new RoundTripSearchResponse(outbound, returnTrip, cheapest);
    }

    // ======================== PRIVATE HELPERS ========================

    private List<FlightSearchResult> applyFilters(List<FlightSearchResult> flights,
                                                   FlightSearchRequest req) {
        return flights.stream()
            .filter(f -> req.getMinPrice() == null ||
                         f.getPricePerPerson().compareTo(req.getMinPrice()) >= 0)
            .filter(f -> req.getMaxPrice() == null ||
                         f.getPricePerPerson().compareTo(req.getMaxPrice()) <= 0)
            .filter(f -> req.getAirlines() == null ||
                         req.getAirlines().contains(f.getAirlineCode()))
            .filter(f -> req.getMaxDuration() == null ||
                         f.getDurationMinutes() <= req.getMaxDuration())
            .filter(f -> matchTimeRange(f.getDepartureTime(), req.getDepartTimeRange()))
            .collect(Collectors.toList());
    }

    private List<FlightSearchResult> applySorting(List<FlightSearchResult> flights,
                                                   SortBy sortBy) {
        Comparator<FlightSearchResult> comparator = switch (sortBy) {
            case PRICE_ASC      -> Comparator.comparing(FlightSearchResult::getPricePerPerson);
            case PRICE_DESC     -> Comparator.comparing(FlightSearchResult::getPricePerPerson).reversed();
            case DURATION_ASC   -> Comparator.comparingInt(FlightSearchResult::getDurationMinutes);
            case DEPARTURE_ASC  -> Comparator.comparing(FlightSearchResult::getDepartureTime);
            case DEPARTURE_DESC -> Comparator.comparing(FlightSearchResult::getDepartureTime).reversed();
            case ARRIVAL_ASC    -> Comparator.comparing(FlightSearchResult::getArrivalTime);
        };
        return flights.stream().sorted(comparator).collect(Collectors.toList());
    }

    private void calculateTotalPrice(FlightSearchResult flight, FlightSearchRequest req) {
        // BR-03: Trẻ em 75%, em bé 10% giá người lớn
        BigDecimal adultPrice  = flight.getPricePerPerson();
        BigDecimal childPrice  = adultPrice.multiply(BigDecimal.valueOf(0.75));
        BigDecimal infantPrice = adultPrice.multiply(BigDecimal.valueOf(0.10));

        BigDecimal total = adultPrice.multiply(BigDecimal.valueOf(req.getAdults()))
            .add(childPrice.multiply(BigDecimal.valueOf(req.getChildren())))
            .add(infantPrice.multiply(BigDecimal.valueOf(req.getInfants())));

        flight.setTotalPrice(total);
    }

    private void tagFlights(List<FlightSearchResult> flights) {
        if (flights.isEmpty()) return;

        BigDecimal minPrice = flights.stream()
            .map(FlightSearchResult::getPricePerPerson)
            .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        int minDuration = flights.stream()
            .mapToInt(FlightSearchResult::getDurationMinutes).min().orElse(0);

        flights.forEach(f -> {
            if (f.getPricePerPerson().compareTo(minPrice) == 0) f.addTag("CHEAPEST");
            if (f.getDurationMinutes() == minDuration)          f.addTag("FASTEST");
        });
    }

    private boolean matchTimeRange(LocalDateTime departure, TimeRange range) {
        if (range == null) return true;
        int hour = departure.getHour();
        return switch (range) {
            case EARLY_MORNING -> hour >= 0  && hour < 6;
            case MORNING       -> hour >= 6  && hour < 12;
            case AFTERNOON     -> hour >= 12 && hour < 18;
            case EVENING       -> hour >= 18;
        };
    }

    private FlightSearchResponse buildResponse(List<FlightSearchResult> results,
                                                FlightSearchRequest req) {
        // Paging — thực hiện SAU khi đã filter + sort
        int start = req.getPage() * req.getSize();
        int end   = Math.min(start + req.getSize(), results.size());
        List<FlightSearchResult> paged = start < results.size()
            ? results.subList(start, end) : List.of();

        // PriceRange từ toàn bộ kết quả (không phải paged)
        BigDecimal minPrice = results.stream()
            .map(FlightSearchResult::getPricePerPerson)
            .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = results.stream()
            .map(FlightSearchResult::getPricePerPerson)
            .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        List<String> airlines = results.stream()
            .map(FlightSearchResult::getAirlineCode).distinct().toList();
        int minDur = results.stream().mapToInt(FlightSearchResult::getDurationMinutes).min().orElse(0);
        int maxDur = results.stream().mapToInt(FlightSearchResult::getDurationMinutes).max().orElse(0);

        return new FlightSearchResponse(
            new FlightSearchResponse.SearchMeta(
                req.getFrom(), req.getTo(), req.getDepartDate(),
                req.getAdults(), req.getChildren(), req.getInfants(),
                req.getClassType().name()
            ),
            paged,
            new FlightSearchResponse.PriceRange(minPrice, maxPrice),
            new FlightSearchResponse.AvailableFilters(
                airlines,
                new FlightSearchResponse.DurationRange(minDur, maxDur)
            )
        );
    }

    private RoundTripSearchResponse.FlightPair findCheapestPair(
            List<FlightSearchResult> outbound,
            List<FlightSearchResult> returnFlights) {
        if (outbound.isEmpty() || returnFlights.isEmpty()) return null;

        // Sau sort PRICE_ASC thì phần tử đầu là rẻ nhất
        FlightSearchResult cheapestOut = outbound.get(0);
        FlightSearchResult cheapestRet = returnFlights.get(0);
        BigDecimal total = cheapestOut.getPricePerPerson().add(cheapestRet.getPricePerPerson());

        return new RoundTripSearchResponse.FlightPair(cheapestOut, cheapestRet, total);
    }

    private String buildCacheKey(FlightSearchRequest r) {
        String filterHash = Integer.toHexString(Objects.hash(
            r.getMinPrice(), r.getMaxPrice(), r.getAirlines(),
            r.getMaxDuration(), r.getDepartTimeRange()
        ));
        return String.format("flight-search:%s:%s:%s:%s:%d:%s:%s",
            r.getFrom(), r.getTo(), r.getDepartDate(),
            r.getClassType(), r.getTotalPassengers(),
            r.getSortBy(), filterHash);
    }
}
```

---

## Bước 9 — Controller

```java
@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
public class FlightSearchController {

    private final FlightSearchService searchService;

    // One-way
    @GetMapping("/search")
    public ResponseEntity<FlightSearchResponse> search(
            @Valid @ModelAttribute FlightSearchRequest request) {
        return ResponseEntity.ok(searchService.search(request));
    }

    // Round-trip
    @GetMapping("/search/round-trip")
    public ResponseEntity<RoundTripSearchResponse> searchRoundTrip(
            @Valid @ModelAttribute FlightSearchRequest request) {
        return ResponseEntity.ok(searchService.searchRoundTrip(request));
    }
}
```

---

## Bước 10 — Exception & GlobalExceptionHandler

```java
public class InvalidSearchException extends RuntimeException {
    public InvalidSearchException(String msg) { super(msg); }
}
```

Thêm vào `GlobalExceptionHandler`:

```java
@ExceptionHandler(InvalidSearchException.class)
public ResponseEntity<?> handleInvalidSearch(InvalidSearchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("code", "INVALID_SEARCH", "message", ex.getMessage()));
}
```

---

## Test bằng Postman

| # | Request | Kết quả mong đợi |
|---|---------|-----------------|
| 1 | `GET /search?from=SGN&to=HAN&departDate=2025-07-01&adults=2` | `200` + danh sách chuyến bay |
| 2 | `GET /search?from=SGN&to=HAN&departDate=2025-01-01&adults=2` | `400` ngày quá khứ |
| 3 | `GET /search` lần 2 cùng params | Nhanh hơn — từ Redis cache |
| 4 | `GET /search?...&sortBy=DURATION_ASC` | Chuyến bay ngắn nhất ở đầu |
| 5 | `GET /search?...&airlines=VJ` | Chỉ hiện VietJet |
| 6 | `GET /search?...&minPrice=500000&maxPrice=1000000` | Lọc theo khoảng giá |
| 7 | `GET /search/round-trip?from=SGN&to=HAN&departDate=2025-07-01&returnDate=2025-07-05` | `outbound` + `returnTrip` + `cheapestCombination` |

---

## Lưu ý quan trọng

- **Redis** phải chạy trước khi test: `docker run -d -p 6379:6379 redis`
- **`@ModelAttribute`** trên Controller — dùng thay vì `@RequestBody` với GET request
- **Constructor tay 19 tham số** trong `FlightSearchResult` — không được thay bằng `@AllArgsConstructor`
- **`@JsonSerialize/@JsonDeserialize` không cần thiết** — `ObjectMapper` bean đã có `JavaTimeModule`, mọi field `LocalDate`/`LocalDateTime` được xử lý tự động cả trong HTTP response lẫn Redis
- **Paging** thực hiện sau filter + sort để đảm bảo kết quả đúng
- **Cache key** bao gồm tất cả params — filter khác nhau sẽ có key khác nhau, không bị lẫn cache
