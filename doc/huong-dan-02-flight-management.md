# Hướng dẫn Implement Service 02 — Flight Management

> **Module:** Flight Service | **Stack:** Spring Boot + PostgreSQL  
> **Dựa theo spec:** `02-flight-management.md` v1.0

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Database Schema & Entity |
| 2 | Enum & Constants |
| 3 | Repository |
| 4 | DTO (Request / Response) |
| 5 | Mapper |
| 6 | FlightService |
| 7 | Controller |
| 8 | Exception Handling |
| 9 | Test |

---

## Bước 1 — Database Schema & Entity

### 1.1 Tạo bảng DB

```sql
CREATE TABLE airports (
    id           BIGSERIAL PRIMARY KEY,
    iata_code    CHAR(3) UNIQUE NOT NULL,
    icao_code    CHAR(4),
    name         VARCHAR(255) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    country      VARCHAR(100) NOT NULL,
    country_code CHAR(2) NOT NULL,
    timezone     VARCHAR(50) NOT NULL,
    latitude     DECIMAL(9,6),
    longitude    DECIMAL(9,6),
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE TABLE airlines (
    id           BIGSERIAL PRIMARY KEY,
    iata_code    CHAR(2) UNIQUE NOT NULL,
    icao_code    CHAR(3),
    name         VARCHAR(255) NOT NULL,
    country      VARCHAR(100),
    logo_url     TEXT,
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE TABLE aircraft_types (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(20) UNIQUE NOT NULL,
    name              VARCHAR(255) NOT NULL,
    total_seats       INT NOT NULL,
    economy_seats     INT NOT NULL,
    business_seats    INT DEFAULT 0,
    first_class_seats INT DEFAULT 0
);

CREATE TABLE flights (
    id               BIGSERIAL PRIMARY KEY,
    flight_number    VARCHAR(10) NOT NULL,
    airline_id       BIGINT NOT NULL REFERENCES airlines(id),
    aircraft_type_id BIGINT REFERENCES aircraft_types(id),
    origin_id        BIGINT NOT NULL REFERENCES airports(id),
    destination_id   BIGINT NOT NULL REFERENCES airports(id),
    departure_time   TIMESTAMP NOT NULL,
    arrival_time     TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL,
    status           VARCHAR(20) DEFAULT 'SCHEDULED',
    delay_minutes    INT DEFAULT 0,
    terminal         VARCHAR(10),
    gate             VARCHAR(10),
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW(),
    CONSTRAINT chk_times CHECK (arrival_time > departure_time),
    CONSTRAINT chk_airports CHECK (origin_id != destination_id),
    UNIQUE (flight_number, CAST(departure_time AS DATE))
);

CREATE TABLE flight_classes (
    id                   BIGSERIAL PRIMARY KEY,
    flight_id            BIGINT NOT NULL REFERENCES flights(id),
    class_type           VARCHAR(20) NOT NULL,
    base_price           DECIMAL(12,2) NOT NULL,
    total_seats          INT NOT NULL,
    available_seats      INT NOT NULL,
    baggage_allowance_kg INT DEFAULT 20,
    carry_on_kg          INT DEFAULT 7,
    is_refundable        BOOLEAN DEFAULT TRUE,
    refund_fee_percent   INT DEFAULT 0,
    CONSTRAINT chk_seats CHECK (available_seats >= 0),
    UNIQUE (flight_id, class_type)
);

CREATE TABLE seats (
    id               BIGSERIAL PRIMARY KEY,
    flight_id        BIGINT NOT NULL REFERENCES flights(id),
    seat_number      VARCHAR(5) NOT NULL,
    class_type       VARCHAR(20) NOT NULL,
    position         VARCHAR(10) NOT NULL,
    row_number       INT NOT NULL,
    is_available     BOOLEAN DEFAULT TRUE,
    is_extra_legroom BOOLEAN DEFAULT FALSE,
    extra_fee        DECIMAL(10,2) DEFAULT 0,
    UNIQUE (flight_id, seat_number)
);

-- Index
CREATE INDEX idx_flights_departure ON flights(departure_time);
CREATE INDEX idx_flights_origin_dest ON flights(origin_id, destination_id);
CREATE INDEX idx_flights_status ON flights(status);
CREATE INDEX idx_seats_flight ON seats(flight_id, is_available);
```

### 1.2 Entity: Airport

```java
@Entity
@Table(name = "airports")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Airport {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 3, unique = true, nullable = false)
    private String iataCode;

    @Column(length = 4)
    private String icaoCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @Column(length = 2, nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String timezone;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 1.3 Entity: Airline

```java
@Entity
@Table(name = "airlines")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Airline {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2, unique = true, nullable = false)
    private String iataCode;

    @Column(length = 3)
    private String icaoCode;

    @Column(nullable = false)
    private String name;

    private String country;
    private String logoUrl;
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 1.4 Entity: AircraftType

```java
@Entity
@Table(name = "aircraft_types")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AircraftType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;       // B787, A320

    @Column(nullable = false)
    private String name;

    private Integer totalSeats;
    private Integer economySeats;
    private Integer businessSeats = 0;
    private Integer firstClassSeats = 0;
}
```

### 1.5 Entity: Flight

```java
@Entity
@Table(name = "flights")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Flight {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id", nullable = false)
    private Airline airline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_id", nullable = false)
    private Airport origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Airport destination;

    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Column(nullable = false)
    private LocalDateTime arrivalTime;

    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status = FlightStatus.SCHEDULED;

    private Integer delayMinutes = 0;
    private String terminal;
    private String gate;

    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL)
    private List<FlightClass> flightClasses = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;
}
```

### 1.6 Entity: FlightClass

```java
@Entity
@Table(name = "flight_classes")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FlightClass {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false)
    private String classType;   // ECONOMY, BUSINESS, FIRST_CLASS

    @Column(nullable = false)
    private BigDecimal basePrice;

    private Integer totalSeats;
    private Integer availableSeats;
    private Integer baggageAllowanceKg = 20;
    private Integer carryOnKg = 7;
    private Boolean isRefundable = true;
    private Integer refundFeePercent = 0;
}
```

### 1.7 Entity: Seat

```java
@Entity
@Table(name = "seats")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Seat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false, length = 5)
    private String seatNumber;   // 12A, 14C

    @Column(nullable = false)
    private String classType;    // ECONOMY, BUSINESS, FIRST_CLASS

    @Column(nullable = false)
    private String position;     // WINDOW, MIDDLE, AISLE

    private Integer rowNumber;
    private Boolean isAvailable = true;
    private Boolean isExtraLegroom = false;
    private BigDecimal extraFee = BigDecimal.ZERO;
}
```

---

## Bước 2 — Enum

```java
public enum FlightStatus {
    SCHEDULED, DELAYED, BOARDING, DEPARTED, ARRIVED, CANCELLED
}
```

---

## Bước 3 — Repository

```java
public interface AirportRepository extends JpaRepository<Airport, Long> {
    Optional<Airport> findByIataCode(String iataCode);
    boolean existsByIataCode(String iataCode);
    List<Airport> findByIsActiveTrue();
}

public interface AirlineRepository extends JpaRepository<Airline, Long> {
    Optional<Airline> findByIataCode(String iataCode);
}

public interface AircraftTypeRepository extends JpaRepository<AircraftType, Long> {
    Optional<AircraftType> findByCode(String code);
}

public interface FlightRepository extends JpaRepository<Flight, Long> {

    // Kiểm tra trùng flight number trong ngày
    @Query("SELECT COUNT(f) > 0 FROM Flight f WHERE f.flightNumber = :flightNumber " +
           "AND CAST(f.departureTime AS LocalDate) = :date AND f.id != :excludeId")
    boolean existsByFlightNumberAndDate(String flightNumber, LocalDate date, Long excludeId);

    List<Flight> findByStatus(FlightStatus status);
}

public interface FlightClassRepository extends JpaRepository<FlightClass, Long> {
    List<FlightClass> findByFlightId(Long flightId);
}

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByFlightId(Long flightId);
    List<Seat> findByFlightIdAndIsAvailableTrue(Long flightId);
}
```

---

## Bước 4 — DTO

```java
// Request tạo chuyến bay
public record CreateFlightRequest(
    @NotBlank String flightNumber,
    @NotNull Long airlineId,
    Long aircraftTypeId,
    @NotBlank String originIata,
    @NotBlank String destinationIata,
    @NotNull LocalDateTime departureTime,
    @NotNull LocalDateTime arrivalTime,
    String terminal,
    String gate,
    @NotEmpty List<CreateFlightClassRequest> classes
) {}

public record CreateFlightClassRequest(
    @NotBlank String classType,
    @NotNull BigDecimal basePrice,
    @NotNull Integer totalSeats,
    Integer baggageAllowanceKg,
    Boolean isRefundable,
    Integer refundFeePercent
) {}

// Request cập nhật trạng thái
public record UpdateFlightStatusRequest(
    @NotNull FlightStatus status,
    Integer delayMinutes,   // Dùng khi status = DELAYED
    String reason
) {}

// Response chuyến bay
public record FlightResponse(
    Long id,
    String flightNumber,
    AirlineInfo airline,
    AirportInfo origin,
    AirportInfo destination,
    LocalDateTime departureTime,
    LocalDateTime arrivalTime,
    Integer durationMinutes,
    String status,
    String terminal,
    String gate,
    List<FlightClassInfo> classes
) {
    public record AirlineInfo(String code, String name, String logoUrl) {}
    public record AirportInfo(String iata, String name, String city) {}
    public record FlightClassInfo(
        String classType, BigDecimal basePrice,
        Integer availableSeats, Integer totalSeats,
        Integer baggageAllowanceKg
    ) {}
}
```

---

## Bước 5 — FlightService

```java
@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightClassRepository flightClassRepository;
    private final SeatRepository seatRepository;
    private final AirportRepository airportRepository;
    private final AirlineRepository airlineRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ===================== AIRPORT =====================

    public List<Airport> getAllAirports() {
        return airportRepository.findByIsActiveTrue();
    }

    public Airport getAirportByIata(String iata) {
        return airportRepository.findByIataCode(iata.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Sân bay không tồn tại: " + iata));
    }

    @Transactional
    public Airport createAirport(Airport airport) {
        if (airportRepository.existsByIataCode(airport.getIataCode())) {
            throw new DuplicateException("Mã IATA đã tồn tại: " + airport.getIataCode());
        }
        return airportRepository.save(airport);
    }

    // ===================== FLIGHT =====================

    @Transactional
    public FlightResponse createFlight(CreateFlightRequest request) {
        // Validate thời gian
        if (!request.arrivalTime().isAfter(request.departureTime())) {
            throw new InvalidFlightException("Giờ đến phải sau giờ khởi hành");
        }

        // Validate sân bay
        Airport origin = getAirportByIata(request.originIata());
        Airport destination = getAirportByIata(request.destinationIata());
        if (origin.getId().equals(destination.getId())) {
            throw new InvalidFlightException("Điểm đi và điểm đến không được trùng nhau");
        }

        // Validate flight number unique trong ngày
        LocalDate departDate = request.departureTime().toLocalDate();
        if (flightRepository.existsByFlightNumberAndDate(request.flightNumber(), departDate, 0L)) {
            throw new DuplicateException("Mã chuyến bay đã tồn tại trong ngày " + departDate);
        }

        // Tìm airline và aircraft
        Airline airline = airlineRepository.findById(request.airlineId())
                .orElseThrow(() -> new NotFoundException("Hãng bay không tồn tại"));
        AircraftType aircraftType = request.aircraftTypeId() != null
                ? aircraftTypeRepository.findById(request.aircraftTypeId()).orElse(null)
                : null;

        // Tạo Flight
        int duration = (int) Duration.between(request.departureTime(), request.arrivalTime()).toMinutes();
        Flight flight = Flight.builder()
                .flightNumber(request.flightNumber())
                .airline(airline)
                .aircraftType(aircraftType)
                .origin(origin)
                .destination(destination)
                .departureTime(request.departureTime())
                .arrivalTime(request.arrivalTime())
                .durationMinutes(duration)
                .status(FlightStatus.SCHEDULED)
                .terminal(request.terminal())
                .gate(request.gate())
                .build();
        flight = flightRepository.save(flight);

        // Tạo FlightClass
        for (CreateFlightClassRequest classReq : request.classes()) {
            FlightClass fc = FlightClass.builder()
                    .flight(flight)
                    .classType(classReq.classType())
                    .basePrice(classReq.basePrice())
                    .totalSeats(classReq.totalSeats())
                    .availableSeats(classReq.totalSeats()) // Ban đầu còn full
                    .baggageAllowanceKg(classReq.baggageAllowanceKg() != null ? classReq.baggageAllowanceKg() : 20)
                    .isRefundable(classReq.isRefundable() != null ? classReq.isRefundable() : true)
                    .refundFeePercent(classReq.refundFeePercent() != null ? classReq.refundFeePercent() : 0)
                    .build();
            flightClassRepository.save(fc);
        }

        // Tự động tạo ghế nếu có aircraft type
        if (aircraftType != null) {
            generateSeats(flight, aircraftType);
        }

        return toFlightResponse(flight);
    }

    @Transactional
    public void updateFlightStatus(Long flightId, UpdateFlightStatusRequest request) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new NotFoundException("Chuyến bay không tồn tại: " + flightId));

        validateStatusTransition(flight.getStatus(), request.status());

        flight.setStatus(request.status());

        if (request.status() == FlightStatus.DELAYED && request.delayMinutes() != null) {
            flight.setDelayMinutes(request.delayMinutes());
            flight.setDepartureTime(flight.getDepartureTime().plusMinutes(request.delayMinutes()));
        }

        flightRepository.save(flight);

        // Publish event nếu hủy chuyến
        if (request.status() == FlightStatus.CANCELLED) {
            eventPublisher.publishEvent(new FlightCancelledEvent(flight));
        }
    }

    // ===================== SEAT GENERATION =====================

    private void generateSeats(Flight flight, AircraftType aircraft) {
        List<Seat> seats = new ArrayList<>();
        int rowNum = 1;

        // First Class: 2 ghế/hàng (A, C)
        for (int i = 0; i < aircraft.getFirstClassSeats() / 2; i++) {
            seats.add(buildSeat(flight, rowNum, "A", "FIRST_CLASS", "WINDOW", false));
            seats.add(buildSeat(flight, rowNum, "C", "FIRST_CLASS", "AISLE", false));
            rowNum++;
        }

        // Business: 4 ghế/hàng (A, C, D, F)
        for (int i = 0; i < aircraft.getBusinessSeats() / 4; i++) {
            seats.add(buildSeat(flight, rowNum, "A", "BUSINESS", "WINDOW", false));
            seats.add(buildSeat(flight, rowNum, "C", "BUSINESS", "AISLE", false));
            seats.add(buildSeat(flight, rowNum, "D", "BUSINESS", "AISLE", false));
            seats.add(buildSeat(flight, rowNum, "F", "BUSINESS", "WINDOW", false));
            rowNum++;
        }

        // Economy: 6 ghế/hàng (A-F), hàng 14-15 là exit row (extra legroom)
        for (int i = 0; i < aircraft.getEconomySeats() / 6; i++) {
            boolean isExtraLegroom = (rowNum == 14 || rowNum == 15);
            seats.add(buildSeat(flight, rowNum, "A", "ECONOMY", "WINDOW", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "B", "ECONOMY", "MIDDLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "C", "ECONOMY", "AISLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "D", "ECONOMY", "AISLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "E", "ECONOMY", "MIDDLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "F", "ECONOMY", "WINDOW", isExtraLegroom));
            rowNum++;
        }

        seatRepository.saveAll(seats);
    }

    private Seat buildSeat(Flight flight, int row, String col,
                           String classType, String position, boolean extraLegroom) {
        return Seat.builder()
                .flight(flight)
                .seatNumber(row + col)
                .classType(classType)
                .position(position)
                .rowNumber(row)
                .isAvailable(true)
                .isExtraLegroom(extraLegroom)
                .extraFee(extraLegroom ? BigDecimal.valueOf(150000) : BigDecimal.ZERO)
                .build();
    }

    // ===================== VALIDATE STATUS TRANSITION =====================

    private void validateStatusTransition(FlightStatus current, FlightStatus next) {
        Map<FlightStatus, Set<FlightStatus>> allowed = Map.of(
            FlightStatus.SCHEDULED, Set.of(FlightStatus.BOARDING, FlightStatus.DELAYED, FlightStatus.CANCELLED),
            FlightStatus.DELAYED,   Set.of(FlightStatus.BOARDING, FlightStatus.CANCELLED),
            FlightStatus.BOARDING,  Set.of(FlightStatus.DEPARTED),
            FlightStatus.DEPARTED,  Set.of(FlightStatus.ARRIVED)
        );
        if (!allowed.getOrDefault(current, Set.of()).contains(next)) {
            throw new InvalidStatusTransitionException(
                "Không thể chuyển từ " + current + " sang " + next
            );
        }
    }

    // ===================== MAPPER =====================

    private FlightResponse toFlightResponse(Flight f) {
        return new FlightResponse(
            f.getId(), f.getFlightNumber(),
            new FlightResponse.AirlineInfo(f.getAirline().getIataCode(), f.getAirline().getName(), f.getAirline().getLogoUrl()),
            new FlightResponse.AirportInfo(f.getOrigin().getIataCode(), f.getOrigin().getName(), f.getOrigin().getCity()),
            new FlightResponse.AirportInfo(f.getDestination().getIataCode(), f.getDestination().getName(), f.getDestination().getCity()),
            f.getDepartureTime(), f.getArrivalTime(), f.getDurationMinutes(),
            f.getStatus().name(), f.getTerminal(), f.getGate(),
            f.getFlightClasses().stream().map(fc -> new FlightResponse.FlightClassInfo(
                fc.getClassType(), fc.getBasePrice(),
                fc.getAvailableSeats(), fc.getTotalSeats(),
                fc.getBaggageAllowanceKg()
            )).toList()
        );
    }
}
```

---

## Bước 6 — Controller

```java
@RestController
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    // ===== PUBLIC =====

    @GetMapping("/api/v1/airports")
    public ResponseEntity<List<Airport>> getAllAirports() {
        return ResponseEntity.ok(flightService.getAllAirports());
    }

    @GetMapping("/api/v1/airports/{iata}")
    public ResponseEntity<Airport> getAirport(@PathVariable String iata) {
        return ResponseEntity.ok(flightService.getAirportByIata(iata));
    }

    @GetMapping("/api/v1/flights/{id}")
    public ResponseEntity<FlightResponse> getFlight(@PathVariable Long id) {
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    // ===== ADMIN ONLY =====

    @PostMapping("/api/v1/admin/airports")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Airport> createAirport(@Valid @RequestBody Airport airport) {
        return ResponseEntity.status(201).body(flightService.createAirport(airport));
    }

    @PostMapping("/api/v1/admin/flights")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<FlightResponse> createFlight(@Valid @RequestBody CreateFlightRequest request) {
        return ResponseEntity.status(201).body(flightService.createFlight(request));
    }

    @PatchMapping("/api/v1/admin/flights/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFlightStatusRequest request) {
        flightService.updateFlightStatus(id, request);
        return ResponseEntity.ok().build();
    }
}
```

---

## Bước 7 — Custom Exception

```java
public class InvalidFlightException extends RuntimeException {
    public InvalidFlightException(String msg) { super(msg); }
}

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String msg) { super(msg); }
}

public class DuplicateException extends RuntimeException {
    public DuplicateException(String msg) { super(msg); }
}
```

Thêm vào `GlobalExceptionHandler`:

```java
@ExceptionHandler({InvalidFlightException.class, InvalidStatusTransitionException.class})
public ResponseEntity<?> handleFlightException(RuntimeException ex) {
    return ResponseEntity.status(400)
            .body(Map.of("code", "INVALID_FLIGHT", "message", ex.getMessage()));
}

@ExceptionHandler(DuplicateException.class)
public ResponseEntity<?> handleDuplicate(DuplicateException ex) {
    return ResponseEntity.status(409)
            .body(Map.of("code", "DUPLICATE", "message", ex.getMessage()));
}
```

---

## Bước 8 — Event khi hủy chuyến (FlightCancelledEvent)

```java
// Event class
public class FlightCancelledEvent {
    private final Flight flight;
    public FlightCancelledEvent(Flight flight) { this.flight = flight; }
    public Flight getFlight() { return flight; }
}

// Listener — xử lý trong module Booking sau này
@Component
@Slf4j
public class FlightCancelledListener {

    @EventListener
    public void onFlightCancelled(FlightCancelledEvent event) {
        // TODO: Implement khi làm đến module Booking
        // 1. Tìm tất cả booking của chuyến bay này
        // 2. Hoàn tiền
        // 3. Gửi email thông báo
        log.info("Flight {} cancelled — processing affected bookings...",
                event.getFlight().getFlightNumber());
    }
}
```

---

## Bước 9 — Test bằng Postman

| # | Request | Kết quả mong đợi |
|---|---------|-----------------|
| 1 | `POST /api/v1/admin/airports` với iataCode mới | `201` + airport object |
| 2 | `POST /api/v1/admin/airports` với iataCode trùng | `409 DUPLICATE` |
| 3 | `POST /api/v1/admin/flights` với dữ liệu hợp lệ | `201` + flight + classes |
| 4 | `POST /api/v1/admin/flights` với arrivalTime < departureTime | `400 INVALID_FLIGHT` |
| 5 | `PATCH /api/v1/admin/flights/{id}/status` SCHEDULED → CANCELLED | `200` |
| 6 | `PATCH /api/v1/admin/flights/{id}/status` ARRIVED → CANCELLED | `400` (không hợp lệ) |
| 7 | `GET /api/v1/airports` | Danh sách sân bay |
| 8 | `GET /api/v1/flights/{id}` | Chi tiết chuyến bay + classes |

---

## Lưu ý quan trọng

- `@PreAuthorize("hasRole('ROLE_ADMIN')")` yêu cầu bật `@EnableMethodSecurity` trong `SecurityConfig`
- Khi hủy chuyến bay (CANCELLED), cần implement `FlightCancelledListener` sau khi hoàn thiện module Booking
- `generateSeats()` chỉ chạy khi có `aircraftTypeId` — nếu không có thì Admin phải tạo ghế thủ công
