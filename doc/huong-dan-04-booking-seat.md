# Hướng dẫn Implement Service 04 — Booking & Seat Selection

> **Module:** Booking Service | **Stack:** Spring Boot + PostgreSQL  
> **Dựa theo spec:** `04-booking-seat.md` v1.0  
> **Phụ thuộc:** Service 02 (Flight Management) phải hoàn thành trước

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Database Schema & Entity |
| 2 | Enum |
| 3 | Repository |
| 4 | DTO (Request / Response) |
| 5 | BookingService — Tạo booking với Pessimistic Lock |
| 6 | BookingExpiryScheduler — Tự động hủy booking hết hạn |
| 7 | Controller |
| 8 | Exception Handling |
| 9 | Test |

---

## Bước 1 — Database Schema & Entity

### 1.1 Tạo bảng DB

```sql
CREATE TABLE bookings (
    id            BIGSERIAL PRIMARY KEY,
    pnr_code      CHAR(6) UNIQUE NOT NULL,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    status        VARCHAR(20) DEFAULT 'PENDING',
    trip_type     VARCHAR(20) DEFAULT 'ONE_WAY',
    subtotal      DECIMAL(12,2) NOT NULL,
    service_fee   DECIMAL(12,2) DEFAULT 0,
    total_price   DECIMAL(12,2) NOT NULL,
    currency      CHAR(3) DEFAULT 'VND',
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20),
    expires_at    TIMESTAMP,
    confirmed_at  TIMESTAMP,
    cancelled_at  TIMESTAMP,
    cancel_reason TEXT,
    refund_amount DECIMAL(12,2),
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE booking_segments (
    id               BIGSERIAL PRIMARY KEY,
    booking_id       BIGINT NOT NULL REFERENCES bookings(id),
    flight_class_id  BIGINT NOT NULL REFERENCES flight_classes(id),
    segment_type     VARCHAR(20) DEFAULT 'OUTBOUND',
    segment_price    DECIMAL(12,2) NOT NULL,
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE passengers (
    id                 BIGSERIAL PRIMARY KEY,
    booking_segment_id BIGINT NOT NULL REFERENCES booking_segments(id),
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    date_of_birth      DATE NOT NULL,
    gender             VARCHAR(10),
    nationality        CHAR(2) NOT NULL,
    id_type            VARCHAR(20) NOT NULL,
    id_number          VARCHAR(50) NOT NULL,
    id_expiry          DATE,
    passenger_type     VARCHAR(10) DEFAULT 'ADULT',
    seat_id            BIGINT REFERENCES seats(id),
    extra_baggage_kg   INT DEFAULT 0,
    meal_preference    VARCHAR(20),
    created_at         TIMESTAMP DEFAULT NOW()
);

-- Index
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_pnr ON bookings(pnr_code);
CREATE INDEX idx_bookings_status ON bookings(status, expires_at);
CREATE INDEX idx_passengers_id_number ON passengers(id_number);
```

### 1.2 Entity: Booking

```java
@Entity
@Table(name = "bookings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Booking {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 6, unique = true, nullable = false)
    private String pnrCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;

    private String tripType = "ONE_WAY";

    @Column(nullable = false)
    private BigDecimal subtotal;

    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    private String currency = "VND";

    @Column(nullable = false)
    private String contactEmail;

    private String contactPhone;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private BigDecimal refundAmount;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private List<BookingSegment> segments = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;
}
```

### 1.3 Entity: BookingSegment

```java
@Entity
@Table(name = "booking_segments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingSegment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_class_id", nullable = false)
    private FlightClass flightClass;

    private String segmentType = "OUTBOUND";   // OUTBOUND | RETURN
    private BigDecimal segmentPrice;

    @OneToMany(mappedBy = "bookingSegment", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Passenger> passengers = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

### 1.4 Entity: Passenger

```java
@Entity
@Table(name = "passengers")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Passenger {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_segment_id", nullable = false)
    private BookingSegment bookingSegment;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    private String gender;

    @Column(length = 2, nullable = false)
    private String nationality;

    @Column(nullable = false)
    private String idType;    // CCCD | PASSPORT

    @Column(nullable = false)
    private String idNumber;

    private LocalDate idExpiry;

    private String passengerType = "ADULT";  // ADULT | CHILD | INFANT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;   // NULL với INFANT

    private Integer extraBaggageKg = 0;
    private String mealPreference;  // STANDARD | VEGETARIAN | HALAL

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

---

## Bước 2 — Enum

```java
public enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED, EXPIRED, COMPLETED
}
```

---

## Bước 3 — Repository

```java
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPnrCode(String pnrCode);
    boolean existsByPnrCode(String pnrCode);

    // Tìm booking hết hạn (dùng cho Scheduler)
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.expiresAt < :now")
    List<Booking> findExpiredPending(@Param("now") LocalDateTime now);

    // Tìm booking của user
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
}

public interface FlightClassRepository extends JpaRepository<FlightClass, Long> {

    // PESSIMISTIC LOCK — chặn concurrent booking trên cùng 1 hạng vé
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fc FROM FlightClass fc WHERE fc.id = :id")
    Optional<FlightClass> findByIdWithLock(@Param("id") Long id);
}

public interface SeatRepository extends JpaRepository<Seat, Long> {

    // PESSIMISTIC LOCK — chặn 2 người chọn cùng 1 ghế
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :ids")
    List<Seat> findAllByIdWithLock(@Param("ids") List<Long> ids);

    // Đánh dấu ghế đã được giữ
    @Modifying
    @Transactional
    @Query("UPDATE Seat s SET s.isAvailable = false WHERE s.id IN :ids")
    void markAsHeld(@Param("ids") List<Long> ids);

    // Trả ghế về pool khi booking hết hạn/hủy
    @Modifying
    @Transactional
    @Query("UPDATE Seat s SET s.isAvailable = true WHERE s.id IN :ids")
    void releaseSeats(@Param("ids") List<Long> ids);

    List<Seat> findByFlightId(Long flightId);
}

public interface BookingSegmentRepository extends JpaRepository<BookingSegment, Long> {
    List<BookingSegment> findByBookingId(Long bookingId);
}

public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    // Kiểm tra trùng CCCD trên cùng chuyến bay (BR-07)
    @Query("""
        SELECT COUNT(p) > 0 FROM Passenger p
        JOIN p.bookingSegment bs
        JOIN bs.flightClass fc
        WHERE fc.flight.id = :flightId
          AND p.idNumber IN :idNumbers
          AND bs.booking.status NOT IN ('CANCELLED', 'EXPIRED')
    """)
    boolean existsDuplicateOnFlight(@Param("flightId") Long flightId,
                                    @Param("idNumbers") List<String> idNumbers);
}
```

---

## Bước 4 — DTO

### 4.1 Request

```java
public record CreateBookingRequest(
    @NotNull Long flightClassId,
    @NotBlank String contactEmail,
    String contactPhone,
    @NotEmpty List<PassengerRequest> passengers,
    List<Long> selectedSeatIds
) {
    // Hành khách không phải INFANT mới chiếm ghế
    public int getNonInfantPassengers() {
        return (int) passengers.stream()
            .filter(p -> !"INFANT".equals(p.passengerType()))
            .count();
    }

    public List<String> getPassengerIdNumbers() {
        return passengers.stream().map(PassengerRequest::idNumber).toList();
    }
}

public record PassengerRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    String gender,
    @NotBlank String nationality,
    @NotBlank String idType,
    @NotBlank String idNumber,
    LocalDate idExpiry,
    String passengerType,   // ADULT | CHILD | INFANT
    Long seatId,            // NULL với INFANT
    Integer extraBaggageKg,
    String mealPreference
) {}
```

### 4.2 Response

```java
// Seat Map DTO
public record SeatMapResponse(
    List<SeatRow> firstClass,
    List<SeatRow> business,
    List<SeatRow> economy
) {
    public static SeatMapResponseBuilder builder() { return new SeatMapResponseBuilder(); }

    public static class SeatMapResponseBuilder {
        private List<SeatRow> firstClass;
        private List<SeatRow> business;
        private List<SeatRow> economy;
        public SeatMapResponseBuilder firstClass(List<SeatRow> v) { this.firstClass = v; return this; }
        public SeatMapResponseBuilder business(List<SeatRow> v) { this.business = v; return this; }
        public SeatMapResponseBuilder economy(List<SeatRow> v) { this.economy = v; return this; }
        public SeatMapResponse build() { return new SeatMapResponse(firstClass, business, economy); }
    }
}

public record SeatRow(int rowNumber, List<SeatInfo> seats) {
    public static SeatRowBuilder builder() { return new SeatRowBuilder(); }
    public static class SeatRowBuilder {
        private int rowNumber;
        private List<SeatInfo> seats;
        public SeatRowBuilder rowNumber(int v) { this.rowNumber = v; return this; }
        public SeatRowBuilder seats(List<SeatInfo> v) { this.seats = v; return this; }
        public SeatRow build() { return new SeatRow(rowNumber, seats); }
    }
}

public record SeatInfo(
    String seatNumber, String position,
    Boolean isAvailable, BigDecimal extraFee, Boolean isExtraLegroom
) {
    public static SeatInfoBuilder builder() { return new SeatInfoBuilder(); }
    public static class SeatInfoBuilder {
        private String seatNumber, position;
        private Boolean isAvailable, isExtraLegroom;
        private BigDecimal extraFee;
        public SeatInfoBuilder seatNumber(String v) { this.seatNumber = v; return this; }
        public SeatInfoBuilder position(String v) { this.position = v; return this; }
        public SeatInfoBuilder isAvailable(Boolean v) { this.isAvailable = v; return this; }
        public SeatInfoBuilder extraFee(BigDecimal v) { this.extraFee = v; return this; }
        public SeatInfoBuilder isExtraLegroom(Boolean v) { this.isExtraLegroom = v; return this; }
        public SeatInfo build() { return new SeatInfo(seatNumber, position, isAvailable, extraFee, isExtraLegroom); }
    }
}

// Cancel Response
public record CancelBookingResponse(
    String pnrCode,
    BigDecimal refundAmount,
    LocalDateTime cancelledAt
) {}
```

### 4.2 Response

```java
public record BookingResponse(
    String pnrCode,
    String status,
    LocalDateTime expiresAt,
    FlightInfo flight,
    List<PassengerInfo> passengers,
    PricingInfo pricing,
    LocalDateTime paymentDeadline
) {
    public record FlightInfo(
        String flightNumber, String from, String to, LocalDateTime departureTime
    ) {}

    public record PassengerInfo(
        String name, String seat, String idNumber
    ) {}

    public record PricingInfo(
        BigDecimal subtotal, BigDecimal serviceFee,
        BigDecimal totalPrice, String currency
    ) {}
}
```

---

## Bước 5 — BookingService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlightClassRepository flightClassRepository;
    private final SeatRepository seatRepository;
    private final PassengerRepository passengerRepository;
    private final BookingSegmentRepository bookingSegmentRepository;

    private static final BigDecimal SERVICE_FEE_PER_PERSON = BigDecimal.valueOf(27500);

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, Long userId) {

        // ① Lấy flight class với PESSIMISTIC LOCK
        FlightClass flightClass = flightClassRepository
                .findByIdWithLock(request.flightClassId())
                .orElseThrow(() -> new NotFoundException("Hạng vé không tồn tại"));

        int passengerCount = request.getNonInfantPassengers();

        // ② Kiểm tra đủ ghế (BR-03)
        if (flightClass.getAvailableSeats() < passengerCount) {
            throw new NotEnoughSeatsException(
                "Chỉ còn " + flightClass.getAvailableSeats() + " ghế trống"
            );
        }

        // ③ Kiểm tra ghế được chọn còn trống (BR-08 — Pessimistic Lock)
        List<Long> seatIds = request.selectedSeatIds() != null ? request.selectedSeatIds() : List.of();
        if (!seatIds.isEmpty()) {
            List<Seat> seats = seatRepository.findAllByIdWithLock(seatIds);
            seats.forEach(seat -> {
                if (!seat.getIsAvailable()) {
                    throw new SeatUnavailableException("Ghế " + seat.getSeatNumber() + " đã được chọn");
                }
            });
        }

        // ④ Kiểm tra trùng CCCD (BR-07)
        List<String> idNumbers = request.getPassengerIdNumbers();
        if (passengerRepository.existsDuplicateOnFlight(
                flightClass.getFlight().getId(), idNumbers)) {
            throw new DuplicatePassengerException("Một hành khách đã có booking trên chuyến bay này");
        }

        // ⑤ Tính giá
        BigDecimal subtotal = flightClass.getBasePrice()
                .multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal serviceFee = SERVICE_FEE_PER_PERSON
                .multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal totalPrice = subtotal.add(serviceFee);

        // ⑥ Tạo Booking
        Booking booking = Booking.builder()
                .pnrCode(generatePNR())
                .user(User.builder().id(userId).build())
                .status(BookingStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(15))  // BR-02
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .subtotal(subtotal)
                .serviceFee(serviceFee)
                .totalPrice(totalPrice)
                .build();
        booking = bookingRepository.save(booking);

        // ⑦ Tạo Segment
        BookingSegment segment = BookingSegment.builder()
                .booking(booking)
                .flightClass(flightClass)
                .segmentType("OUTBOUND")
                .segmentPrice(subtotal)
                .build();
        segment = bookingSegmentRepository.save(segment);

        // ⑧ Tạo Passengers
        for (PassengerRequest pr : request.passengers()) {
            Seat seat = (pr.seatId() != null && !"INFANT".equals(pr.passengerType()))
                ? seatRepository.findById(pr.seatId()).orElse(null)
                : null;

            Passenger passenger = Passenger.builder()
                    .bookingSegment(segment)
                    .firstName(pr.firstName())
                    .lastName(pr.lastName())
                    .dateOfBirth(pr.dateOfBirth())
                    .gender(pr.gender())
                    .nationality(pr.nationality())
                    .idType(pr.idType())
                    .idNumber(pr.idNumber())
                    .idExpiry(pr.idExpiry())
                    .passengerType(pr.passengerType() != null ? pr.passengerType() : "ADULT")
                    .seat(seat)
                    .extraBaggageKg(pr.extraBaggageKg() != null ? pr.extraBaggageKg() : 0)
                    .mealPreference(pr.mealPreference())
                    .build();
            passengerRepository.save(passenger);
        }

        // ⑨ Đánh dấu ghế là đã giữ (BR-03)
        if (!seatIds.isEmpty()) {
            seatRepository.markAsHeld(seatIds);
        }

        // ⑩ Giảm available_seats
        flightClass.setAvailableSeats(flightClass.getAvailableSeats() - passengerCount);
        flightClassRepository.save(flightClass);

        return toBookingResponse(booking, flightClass, request);
    }

    // Lấy booking theo PNR
    public BookingResponse getBooking(String pnrCode, Long userId) {
        Booking booking = bookingRepository.findByPnrCode(pnrCode)
                .orElseThrow(() -> new NotFoundException("Booking không tồn tại: " + pnrCode));
        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền xem booking này");
        }
        FlightClass fc = booking.getSegments().get(0).getFlightClass();
        return toBookingResponse(booking, fc, null);
    }

    // Hủy booking khi hết hạn (gọi từ Scheduler)
    @Transactional
    public void expireBooking(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        // Trả ghế về pool
        releaseSeatsForBooking(booking);

        log.info("Booking {} expired and seats released", booking.getPnrCode());
    }

    // Hủy booking theo yêu cầu của user
    @Transactional
    public CancelBookingResponse cancelBooking(String pnrCode, Long userId) {
        Booking booking = bookingRepository.findByPnrCode(pnrCode)
                .orElseThrow(() -> new NotFoundException("Booking không tồn tại: " + pnrCode));

        // Kiểm tra quyền sở hữu
        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền hủy booking này");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingException("Chỉ có thể hủy booking đã xác nhận");
        }

        // Tính tiền hoàn theo BR-10
        BigDecimal refundAmount = calculateRefund(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setRefundAmount(refundAmount);
        bookingRepository.save(booking);

        releaseSeatsForBooking(booking);

        return new CancelBookingResponse(pnrCode, refundAmount, LocalDateTime.now());
    }

    // ======================== HELPERS ========================

    private BigDecimal calculateRefund(Booking booking) {
        // BR-10: Hủy trước 24h → hoàn 70%, trong 24h → 0%
        // Cần lấy departureTime từ segment
        LocalDateTime departureTime = booking.getSegments().get(0)
                .getFlightClass().getFlight().getDepartureTime();

        long hoursUntilDeparture = Duration.between(LocalDateTime.now(), departureTime).toHours();

        if (hoursUntilDeparture >= 24) {
            return booking.getTotalPrice().multiply(BigDecimal.valueOf(0.70));
        }
        return BigDecimal.ZERO;
    }

    private void releaseSeatsForBooking(Booking booking) {
        // Lấy tất cả seat IDs trong booking
        List<Long> seatIds = booking.getSegments().stream()
            .flatMap(seg -> seg.getPassengers().stream())
            .filter(p -> p.getSeat() != null)
            .map(p -> p.getSeat().getId())
            .toList();

        if (!seatIds.isEmpty()) {
            seatRepository.releaseSeats(seatIds);
        }

        // Tăng available_seats trở lại
        booking.getSegments().forEach(seg -> {
            FlightClass fc = seg.getFlightClass();
            int nonInfants = (int) seg.getPassengers().stream()
                .filter(p -> !"INFANT".equals(p.getPassengerType())).count();
            fc.setAvailableSeats(fc.getAvailableSeats() + nonInfants);
            flightClassRepository.save(fc);
        });
    }

    private String generatePNR() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Bỏ I, O, 0, 1 dễ nhầm
        String pnr;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            }
            pnr = sb.toString();
        } while (bookingRepository.existsByPnrCode(pnr));
        return pnr;
    }

    private BookingResponse toBookingResponse(Booking booking, FlightClass fc,
                                              CreateBookingRequest request) {
        Flight flight = fc.getFlight();

        // Lấy passenger list từ DB nếu không có request (trường hợp getBooking)
        List<BookingResponse.PassengerInfo> passengerInfos;
        if (request != null) {
            passengerInfos = request.passengers().stream().map(p -> new BookingResponse.PassengerInfo(
                p.firstName() + " " + p.lastName(),
                p.seatId() != null ? p.seatId().toString() : "N/A",
                p.idNumber()
            )).toList();
        } else {
            passengerInfos = booking.getSegments().stream()
                .flatMap(seg -> seg.getPassengers().stream())
                .map(p -> new BookingResponse.PassengerInfo(
                    p.getFirstName() + " " + p.getLastName(),
                    p.getSeat() != null ? p.getSeat().getSeatNumber() : "N/A",
                    p.getIdNumber()
                )).toList();
        }

        return new BookingResponse(
            booking.getPnrCode(),
            booking.getStatus().name(),
            booking.getExpiresAt(),
            new BookingResponse.FlightInfo(
                flight.getFlightNumber(),
                flight.getOrigin().getIataCode(),
                flight.getDestination().getIataCode(),
                flight.getDepartureTime()
            ),
            passengerInfos,
            new BookingResponse.PricingInfo(
                booking.getSubtotal(), booking.getServiceFee(),
                booking.getTotalPrice(), booking.getCurrency()
            ),
            booking.getExpiresAt()
        );
    }
}
```

---

## Bước 6 — Scheduler hủy booking hết hạn

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    // Chạy mỗi 1 phút
    @Scheduled(fixedDelay = 60_000)
    public void cancelExpiredBookings() {
        List<Booking> expired = bookingRepository.findExpiredPending(LocalDateTime.now());

        if (!expired.isEmpty()) {
            log.info("Found {} expired bookings to cancel", expired.size());
            expired.forEach(booking -> {
                try {
                    bookingService.expireBooking(booking);
                } catch (Exception e) {
                    log.error("Failed to expire booking {}: {}", booking.getPnrCode(), e.getMessage());
                }
            });
        }
    }
}
```

Bật `@EnableScheduling` trong main class hoặc config:

```java
@SpringBootApplication
@EnableScheduling
public class Application { ... }
```

---

## Bước 7 — Seat Map API

```java
@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final SeatRepository seatRepository;

    public SeatMapResponse getSeatMap(Long flightId) {
        List<Seat> allSeats = seatRepository.findByFlightId(flightId);

        // Group: class → row → seats
        Map<String, Map<Integer, List<Seat>>> grouped = allSeats.stream()
            .collect(Collectors.groupingBy(
                Seat::getClassType,
                Collectors.groupingBy(Seat::getRowNumber)
            ));

        return SeatMapResponse.builder()
            .firstClass(buildSection(grouped.get("FIRST_CLASS")))
            .business(buildSection(grouped.get("BUSINESS")))
            .economy(buildSection(grouped.get("ECONOMY")))
            .build();
    }

    private List<SeatRow> buildSection(Map<Integer, List<Seat>> rowMap) {
        if (rowMap == null) return List.of();
        return rowMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> SeatRow.builder()
                .rowNumber(e.getKey())
                .seats(e.getValue().stream().map(s -> SeatInfo.builder()
                    .seatNumber(s.getSeatNumber())
                    .position(s.getPosition())
                    .isAvailable(s.getIsAvailable())
                    .extraFee(s.getExtraFee())
                    .isExtraLegroom(s.getIsExtraLegroom())
                    .build()).toList())
                .build())
            .toList();
    }
}
```

---

## Bước 8 — Controller

```java
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final SeatMapService seatMapService;

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(201).body(bookingService.createBooking(request, user.getId()));
    }

    @GetMapping("/bookings/{pnr}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable String pnr,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getBooking(pnr, user.getId()));
    }

    @DeleteMapping("/bookings/{pnr}")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable String pnr,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.cancelBooking(pnr, user.getId()));
    }

    @GetMapping("/flights/{flightId}/seats")
    public ResponseEntity<SeatMapResponse> getSeatMap(@PathVariable Long flightId) {
        return ResponseEntity.ok(seatMapService.getSeatMap(flightId));
    }
}
```

---

## Bước 9 — Custom Exception

```java
public class NotEnoughSeatsException extends RuntimeException {
    public NotEnoughSeatsException(String msg) { super(msg); }
}
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String msg) { super(msg); }
}
public class DuplicatePassengerException extends RuntimeException {
    public DuplicatePassengerException(String msg) { super(msg); }
}
public class InvalidBookingException extends RuntimeException {
    public InvalidBookingException(String msg) { super(msg); }
}
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String msg) { super(msg); }
}
```

---

## Bước 10 — Test bằng Postman

| # | Request | Kết quả mong đợi |
|---|---------|-----------------|
| 1 | `POST /bookings` với dữ liệu hợp lệ | `201` + pnrCode + expiresAt |
| 2 | `POST /bookings` với ghế đã bị chọn | `409 SEAT_UNAVAILABLE` |
| 3 | `POST /bookings` với CCCD trùng trên cùng chuyến | `409 DUPLICATE_PASSENGER` |
| 4 | `POST /bookings` khi không đủ ghế | `409 NOT_ENOUGH_SEATS` |
| 5 | `GET /flights/{id}/seats` | Sơ đồ ghế theo hạng |
| 6 | Đợi 15 phút không thanh toán | Booking → EXPIRED, ghế trả về pool |
| 7 | `DELETE /bookings/{pnr}` trước 24h | `200` + refundAmount = 70% tổng tiền |
| 8 | `DELETE /bookings/{pnr}` trong 24h | `200` + refundAmount = 0 |

---

## Lưu ý quan trọng

- **Pessimistic Lock** (`PESSIMISTIC_WRITE`) ngăn 2 request đồng thời book cùng ghế — KHÔNG được bỏ qua
- Toàn bộ logic tạo booking nằm trong **1 transaction** — nếu bất kỳ bước nào fail thì rollback toàn bộ
- `@EnableScheduling` phải được bật để Scheduler hoạt động
- Khi tích hợp với Payment Service (Service 05), trạng thái booking sẽ được chuyển từ `PENDING` → `CONFIRMED` sau khi thanh toán thành công
