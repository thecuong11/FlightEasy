# Service 03 — Tìm kiếm & Lọc chuyến bay (Flight Search)

> **Module:** Search Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P0 — Tính năng cốt lõi, ảnh hưởng trực tiếp đến trải nghiệm người dùng

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Người dùng (kể cả chưa đăng nhập) có thể tìm kiếm chuyến bay theo điểm đi, điểm đến, ngày bay, số hành khách và hạng vé. Kết quả được lọc, sắp xếp và cache để đảm bảo tốc độ.

### 1.2 Các loại tìm kiếm

| Loại | Mô tả |
|------|-------|
| **One-way** | 1 chiều: SGN → HAN ngày X |
| **Round-trip** | Khứ hồi: SGN → HAN ngày X, HAN → SGN ngày Y |
| **Multi-city** | Nhiều chặng (scope mở rộng sau) |

### 1.3 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Ngày tìm kiếm phải từ ngày mai trở đi |
| BR-02 | Tổng hành khách (người lớn + trẻ em + em bé) không vượt quá 9 |
| BR-03 | Em bé (< 2 tuổi) không chiếm ghế, giá vé 10% người lớn |
| BR-04 | Chỉ trả về chuyến bay có status = SCHEDULED hoặc DELAYED |
| BR-05 | Chỉ trả về chuyến bay có đủ ghế trống cho số hành khách yêu cầu |
| BR-06 | Kết quả tìm kiếm cache Redis 5 phút |
| BR-07 | Giá hiển thị là giá/người — chưa bao gồm phí dịch vụ |
| BR-08 | Sắp xếp mặc định: giá thấp nhất |

### 1.4 Các tiêu chí lọc (Filter)

| Filter | Loại | Mô tả |
|--------|------|-------|
| Khoảng giá | Range | Min price — Max price |
| Hãng bay | Multi-select | VN, VJ, QH... |
| Số điểm dừng | Single | Bay thẳng / 1 điểm dừng / 2+ điểm dừng |
| Khung giờ khởi hành | Multi-select | Sáng sớm / Buổi sáng / Buổi chiều / Tối |
| Hạng vé | Single | Economy / Business / First |
| Thời gian bay | Range | Ngắn hơn X giờ |

### 1.5 Các tùy chọn sắp xếp (Sort)

| Option | Mô tả |
|--------|-------|
| `PRICE_ASC` | Giá thấp nhất (mặc định) |
| `PRICE_DESC` | Giá cao nhất |
| `DURATION_ASC` | Bay nhanh nhất |
| `DEPARTURE_ASC` | Khởi hành sớm nhất |
| `DEPARTURE_DESC` | Khởi hành muộn nhất |
| `ARRIVAL_ASC` | Đến sớm nhất |

---

## 2. Flow Diagram

### 2.1 Flow tìm kiếm chuyến bay

```
Client                SearchController        SearchService        Redis       DB
  │                        │                      │                 │           │
  │ GET /flights/search     │                      │                 │           │
  │ ?from=SGN&to=HAN        │                      │                 │           │
  │ &date=2025-06-15        │                      │                 │           │
  │ &passengers=2           │                      │                 │           │
  │ &class=ECONOMY          │                      │                 │           │
  │───────────────────────►│                      │                 │           │
  │                        │ search(request)       │                 │           │
  │                        │─────────────────────►│                 │           │
  │                        │                      │ buildCacheKey() │           │
  │                        │                      │ get(key) ──────►│           │
  │                        │                      │                 │           │
  │                        │                      │ [Cache HIT]     │           │
  │                        │                      │◄────────────────│           │
  │◄───────────────────────│                      │                 │           │
  │ 200 OK (từ cache)       │                      │                 │           │
  │                        │                      │                 │           │
  │                        │                      │ [Cache MISS]    │           │
  │                        │                      │ queryFlights()  │           │
  │                        │                      │────────────────────────────►│
  │                        │                      │ applyFilters()  │           │
  │                        │                      │ applySorting()  │           │
  │                        │                      │ calculatePrice()│           │
  │                        │                      │ set(key, 5min) ►│           │
  │◄───────────────────────│                      │                 │           │
  │ 200 OK (từ DB)          │                      │                 │           │
```

### 2.2 Flow tìm kiếm khứ hồi (Round-trip)

```
Client              SearchController          SearchService
  │                      │                        │
  │ GET /flights/search   │                        │
  │ ?type=ROUND_TRIP      │                        │
  │ &from=SGN&to=HAN      │                        │
  │ &departDate=Jun15     │                        │
  │ &returnDate=Jun20     │                        │
  │─────────────────────►│                        │
  │                      │ searchRoundTrip()       │
  │                      │───────────────────────►│
  │                      │                        │ Chạy song song (CompletableFuture):
  │                      │                        │ ┌─ searchOutbound(SGN→HAN, Jun15)
  │                      │                        │ └─ searchReturn(HAN→SGN, Jun20)
  │                      │                        │
  │                      │                        │ Ghép kết quả
  │                      │                        │ Tính tổng giá khứ hồi
  │◄─────────────────────│                        │
  │ 200 OK               │                        │
  │ { outbound: [...],    │                        │
  │   return: [...],      │                        │
  │   cheapestCombination }                        │
```

---

## 3. Database Query Strategy

### 3.1 Query tìm kiếm chính (PostgreSQL)

```sql
SELECT
    f.id,
    f.flight_number,
    f.departure_time,
    f.arrival_time,
    f.duration_minutes,
    f.status,
    al.name AS airline_name,
    al.iata_code AS airline_code,
    al.logo_url,
    ap_origin.iata_code AS origin_iata,
    ap_origin.name AS origin_name,
    ap_dest.iata_code AS dest_iata,
    ap_dest.name AS dest_name,
    fc.base_price,
    fc.available_seats,
    fc.baggage_allowance_kg
FROM flights f
JOIN airlines al ON f.airline_id = al.id
JOIN airports ap_origin ON f.origin_id = ap_origin.id
JOIN airports ap_dest ON f.destination_id = ap_dest.id
JOIN flight_classes fc ON fc.flight_id = f.id
WHERE
    ap_origin.iata_code = :originIata
    AND ap_dest.iata_code = :destIata
    AND DATE(f.departure_time AT TIME ZONE 'Asia/Ho_Chi_Minh') = :departureDate
    AND f.status IN ('SCHEDULED', 'DELAYED')
    AND fc.class_type = :classType
    AND fc.available_seats >= :passengerCount
    -- Dynamic filters
    AND (:minPrice IS NULL OR fc.base_price >= :minPrice)
    AND (:maxPrice IS NULL OR fc.base_price <= :maxPrice)
    AND (:airlineCodes IS NULL OR al.iata_code = ANY(:airlineCodes))
ORDER BY fc.base_price ASC  -- Dynamic sort
LIMIT 50;
```

### 3.2 Cache Key Strategy

```
flight-search:{originIata}:{destIata}:{date}:{classType}:{passengers}:{sortBy}:{filterHash}

Ví dụ:
flight-search:SGN:HAN:2025-06-15:ECONOMY:2:PRICE_ASC:a1b2c3d4
```

---

## 4. API Specification

### GET `/api/v1/flights/search`

**Query Parameters:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| `from` | String | ✅ | IATA sân bay đi (VD: SGN) |
| `to` | String | ✅ | IATA sân bay đến (VD: HAN) |
| `departDate` | Date | ✅ | Ngày đi (yyyy-MM-dd) |
| `returnDate` | Date | ❌ | Ngày về (nếu khứ hồi) |
| `adults` | Int | ✅ | Số người lớn (≥1) |
| `children` | Int | ❌ | Số trẻ em 2-11 tuổi (default 0) |
| `infants` | Int | ❌ | Số em bé < 2 tuổi (default 0) |
| `class` | Enum | ❌ | ECONOMY / BUSINESS / FIRST_CLASS (default ECONOMY) |
| `minPrice` | Long | ❌ | Giá tối thiểu (VNĐ) |
| `maxPrice` | Long | ❌ | Giá tối đa (VNĐ) |
| `airlines` | String | ❌ | Lọc hãng bay, phân cách bởi dấu phẩy (VN,VJ) |
| `maxDuration` | Int | ❌ | Thời gian bay tối đa (phút) |
| `departTimeRange` | Enum | ❌ | EARLY_MORNING / MORNING / AFTERNOON / EVENING |
| `sortBy` | Enum | ❌ | PRICE_ASC (default) / DURATION_ASC / DEPARTURE_ASC |
| `page` | Int | ❌ | Trang (default 0) |
| `size` | Int | ❌ | Số kết quả/trang (default 20, max 50) |

**Ví dụ request:**
```
GET /api/v1/flights/search?from=SGN&to=HAN&departDate=2025-06-15&adults=2&class=ECONOMY&sortBy=PRICE_ASC&airlines=VN,VJ
```

**Response 200:**
```json
{
  "searchId": "srch_abc123",
  "totalResults": 8,
  "searchParams": {
    "from": "SGN", "to": "HAN",
    "departDate": "2025-06-15",
    "passengers": { "adults": 2, "children": 0, "infants": 0 },
    "class": "ECONOMY"
  },
  "flights": [
    {
      "id": 101,
      "flightNumber": "VJ123",
      "airline": {
        "code": "VJ",
        "name": "VietJet Air",
        "logoUrl": "https://cdn.flighteasy.vn/airlines/vj.png"
      },
      "origin": {
        "iata": "SGN",
        "name": "Tân Sơn Nhất International Airport",
        "city": "Hồ Chí Minh",
        "terminal": "T1"
      },
      "destination": {
        "iata": "HAN",
        "name": "Nội Bài International Airport",
        "city": "Hà Nội"
      },
      "departureTime": "2025-06-15T06:00:00",
      "arrivalTime": "2025-06-15T08:10:00",
      "durationMinutes": 130,
      "status": "SCHEDULED",
      "pricePerPerson": 750000,
      "totalPrice": 1500000,
      "availableSeats": 45,
      "baggageAllowanceKg": 20,
      "isRefundable": false,
      "tags": ["CHEAPEST", "FASTEST"]
    }
  ],
  "priceRange": { "min": 750000, "max": 3200000 },
  "availableFilters": {
    "airlines": ["VJ", "VN", "QH"],
    "durationRange": { "min": 120, "max": 145 }
  }
}
```

---

## 5. Code mẫu

### SearchService — Query + Filter + Cache

```java
@Service
@RequiredArgsConstructor
public class FlightSearchService {

    private final FlightRepository flightRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FlightSearchMapper mapper;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @SuppressWarnings("unchecked")
    public FlightSearchResponse search(FlightSearchRequest request) {
        // Validate
        if (request.getDepartDate().isBefore(LocalDate.now().plusDays(1))) {
            throw new InvalidSearchException("Ngày tìm kiếm phải từ ngày mai trở đi");
        }

        String cacheKey = buildCacheKey(request);

        // Thử lấy từ cache trước
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (FlightSearchResponse) cached;
        }

        // Query DB
        List<FlightSearchResult> results = flightRepository.searchFlights(
            request.getFrom(),
            request.getTo(),
            request.getDepartDate(),
            request.getClassType(),
            request.getTotalPassengers()
        );

        // Apply dynamic filters
        results = applyFilters(results, request);

        // Apply sorting
        results = applySorting(results, request.getSortBy());

        // Tính giá theo số hành khách
        results.forEach(r -> calculateTotalPrice(r, request));

        // Tag chuyến bay đặc biệt
        tagFlights(results);

        FlightSearchResponse response = mapper.toResponse(results, request);

        // Lưu cache 5 phút
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);

        return response;
    }

    private List<FlightSearchResult> applyFilters(List<FlightSearchResult> flights,
                                                   FlightSearchRequest request) {
        return flights.stream()
            .filter(f -> request.getMinPrice() == null || f.getPricePerPerson().compareTo(request.getMinPrice()) >= 0)
            .filter(f -> request.getMaxPrice() == null || f.getPricePerPerson().compareTo(request.getMaxPrice()) <= 0)
            .filter(f -> request.getAirlines() == null || request.getAirlines().contains(f.getAirlineCode()))
            .filter(f -> request.getMaxDuration() == null || f.getDurationMinutes() <= request.getMaxDuration())
            .filter(f -> matchTimeRange(f.getDepartureTime(), request.getDepartTimeRange()))
            .collect(Collectors.toList());
    }

    private List<FlightSearchResult> applySorting(List<FlightSearchResult> flights, SortBy sortBy) {
        Comparator<FlightSearchResult> comparator = switch (sortBy) {
            case PRICE_ASC      -> Comparator.comparing(FlightSearchResult::getPricePerPerson);
            case PRICE_DESC     -> Comparator.comparing(FlightSearchResult::getPricePerPerson).reversed();
            case DURATION_ASC   -> Comparator.comparingInt(FlightSearchResult::getDurationMinutes);
            case DEPARTURE_ASC  -> Comparator.comparing(FlightSearchResult::getDepartureTime);
            case DEPARTURE_DESC -> Comparator.comparing(FlightSearchResult::getDepartureTime).reversed();
            case ARRIVAL_ASC    -> Comparator.comparing(FlightSearchResult::getArrivalTime);
            default             -> Comparator.comparing(FlightSearchResult::getPricePerPerson);
        };
        return flights.stream().sorted(comparator).collect(Collectors.toList());
    }

    private void calculateTotalPrice(FlightSearchResult flight, FlightSearchRequest request) {
        BigDecimal adultPrice = flight.getPricePerPerson();
        BigDecimal childPrice = adultPrice.multiply(BigDecimal.valueOf(0.75));  // 75% giá người lớn
        BigDecimal infantPrice = adultPrice.multiply(BigDecimal.valueOf(0.1));  // 10% giá người lớn

        BigDecimal total = adultPrice.multiply(BigDecimal.valueOf(request.getAdults()))
            .add(childPrice.multiply(BigDecimal.valueOf(request.getChildren())))
            .add(infantPrice.multiply(BigDecimal.valueOf(request.getInfants())));

        flight.setTotalPrice(total);
    }

    private void tagFlights(List<FlightSearchResult> flights) {
        if (flights.isEmpty()) return;

        BigDecimal minPrice = flights.stream().map(FlightSearchResult::getPricePerPerson)
            .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        int minDuration = flights.stream().mapToInt(FlightSearchResult::getDurationMinutes).min().orElse(0);

        flights.forEach(f -> {
            if (f.getPricePerPerson().equals(minPrice)) f.addTag("CHEAPEST");
            if (f.getDurationMinutes() == minDuration) f.addTag("FASTEST");
        });
    }

    private boolean matchTimeRange(LocalDateTime departure, TimeRange range) {
        if (range == null) return true;
        int hour = departure.getHour();
        return switch (range) {
            case EARLY_MORNING -> hour >= 0 && hour < 6;
            case MORNING       -> hour >= 6 && hour < 12;
            case AFTERNOON     -> hour >= 12 && hour < 18;
            case EVENING       -> hour >= 18 && hour < 24;
        };
    }

    private String buildCacheKey(FlightSearchRequest r) {
        String filterHash = Integer.toHexString(Objects.hash(
            r.getMinPrice(), r.getMaxPrice(), r.getAirlines(), r.getMaxDuration(), r.getDepartTimeRange()
        ));
        return String.format("flight-search:%s:%s:%s:%s:%d:%s:%s",
            r.getFrom(), r.getTo(), r.getDepartDate(),
            r.getClassType(), r.getTotalPassengers(),
            r.getSortBy(), filterHash);
    }
}
```

### CompletableFuture cho Round-trip search

```java
public RoundTripSearchResponse searchRoundTrip(RoundTripSearchRequest request) {
    // Tìm 2 chiều song song — giảm latency từ 2x xuống 1x
    CompletableFuture<FlightSearchResponse> outboundFuture = CompletableFuture.supplyAsync(
        () -> search(request.toOutboundRequest()), searchExecutor
    );
    CompletableFuture<FlightSearchResponse> returnFuture = CompletableFuture.supplyAsync(
        () -> search(request.toReturnRequest()), searchExecutor
    );

    CompletableFuture.allOf(outboundFuture, returnFuture).join();

    FlightSearchResponse outbound = outboundFuture.join();
    FlightSearchResponse returnTrip = returnFuture.join();

    // Tìm tổ hợp rẻ nhất
    FlightPair cheapestPair = findCheapestCombination(
        outbound.getFlights(), returnTrip.getFlights()
    );

    return RoundTripSearchResponse.builder()
        .outbound(outbound)
        .returnTrip(returnTrip)
        .cheapestCombination(cheapestPair)
        .build();
}
```
