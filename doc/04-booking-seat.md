# Service 04 — Đặt vé & Chọn ghế (Booking & Seat Selection)

> **Module:** Booking Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P0 — Tính năng kinh doanh cốt lõi

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Người dùng đã đăng nhập chọn chuyến bay, nhập thông tin hành khách, chọn ghế ngồi và xác nhận booking. Hệ thống tạm giữ ghế trong 15 phút để người dùng hoàn tất thanh toán. Nếu không thanh toán kịp, booking tự động hủy và ghế được trả lại.

### 1.2 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Mỗi booking được cấp mã PNR duy nhất gồm 6 ký tự (chữ + số) |
| BR-02 | Booking hết hạn sau 15 phút nếu chưa thanh toán |
| BR-03 | Số ghế `available_seats` giảm ngay khi booking được tạo (tạm giữ) |
| BR-04 | Nếu booking hết hạn/hủy, `available_seats` tăng lại |
| BR-05 | Mỗi hành khách phải có: họ tên, ngày sinh, số CCCD/Passport |
| BR-06 | Em bé (< 2 tuổi) không cần chọn ghế |
| BR-07 | Không thể đặt 2 booking cùng chuyến bay cho cùng 1 hành khách (trùng CCCD) |
| BR-08 | Ghế đã chọn bởi booking khác không được chọn (dùng Pessimistic Lock) |
| BR-09 | Phí thay đổi ghế sau khi đặt: 50,000 VNĐ/ghế |
| BR-10 | Hủy vé trước 24h: hoàn 70%. Hủy trong 24h: hoàn 0% |

### 1.3 Vòng đời Booking

```
                     [Tạo booking]
                          │
                          ▼
                       PENDING ──── (15 phút) ──────► EXPIRED
                          │                               │
                   [Thanh toán OK]              [Ghế trả về pool]
                          │
                          ▼
                      CONFIRMED ──────────────────► CANCELLED
                          │                        (theo chính sách)
                   [Chuyến bay xong]                    │
                          │                    [Hoàn tiền nếu đủ điều kiện]
                          ▼
                      COMPLETED
```

---

## 2. Flow Diagram

### 2.1 Flow tạo Booking

```
Client           BookingController      BookingService        DB (Transaction)
  │                    │                     │                      │
  │ POST /bookings      │                     │                      │
  │ {flightClassId,     │                     │                      │
  │  passengers,        │                     │                      │
  │  selectedSeats}     │                     │                      │
  │───────────────────►│                     │                      │
  │                    │ createBooking()      │                      │
  │                    │────────────────────►│                      │
  │                    │                     │ BEGIN TRANSACTION    │
  │                    │                     │                      │
  │                    │                     │ ① Check available_seats
  │                    │                     │ (với LOCK)           │
  │                    │                     │─────────────────────►│
  │                    │                     │ [Không đủ ghế → 409] │
  │                    │                     │                      │
  │                    │                     │ ② Lock từng ghế được chọn
  │                    │                     │ (Pessimistic Write Lock)
  │                    │                     │─────────────────────►│
  │                    │                     │ [Ghế bị chiếm → 409] │
  │                    │                     │                      │
  │                    │                     │ ③ Tạo Booking        │
  │                    │                     │ generatePNR()        │
  │                    │                     │ setExpiry(+15 min)   │
  │                    │                     │─────────────────────►│
  │                    │                     │                      │
  │                    │                     │ ④ Tạo Passengers     │
  │                    │                     │─────────────────────►│
  │                    │                     │                      │
  │                    │                     │ ⑤ Mark seats as HELD │
  │                    │                     │─────────────────────►│
  │                    │                     │                      │
  │                    │                     │ ⑥ Giảm available_seats
  │                    │                     │─────────────────────►│
  │                    │                     │                      │
  │                    │                     │ COMMIT               │
  │◄───────────────────│                     │                      │
  │ 201 Created         │                     │                      │
  │ {pnr, expiresAt,    │                     │                      │
  │  totalPrice,        │                     │                      │
  │  paymentRequired}   │                     │                      │
```

### 2.2 Flow Booking hết hạn (Scheduled Job)

```
Scheduler (mỗi 1 phút)      BookingService            DB
        │                        │                      │
        │ cancelExpiredBookings() │                      │
        │───────────────────────►│                      │
        │                        │ findExpiredPending() │
        │                        │─────────────────────►│
        │                        │◄─────────────────────│
        │                        │ [danh sách bookings] │
        │                        │                      │
        │                        │ forEach booking:     │
        │                        │ ① status = EXPIRED   │
        │                        │ ② release seats      │
        │                        │    (available_seats++)
        │                        │ ③ mark seats = AVAILABLE
        │                        │─────────────────────►│
        │                        │ COMMIT               │
```

### 2.3 Flow Hủy vé (User)

```
Client          BookingController      BookingService     PaymentService
  │                   │                    │                   │
  │ DELETE /bookings   │                    │                   │
  │ /{pnr}             │                    │                   │
  │──────────────────►│                    │                   │
  │                   │ cancelBooking()     │                   │
  │                   │───────────────────►│                   │
  │                   │                    │ findBooking(pnr)  │
  │                   │                    │ checkOwnership()  │
  │                   │                    │ calculateRefund() │
  │                   │                    │ (theo BR-10)      │
  │                   │                    │                   │
  │                   │                    │ [Đủ điều kiện]    │
  │                   │                    │ processRefund()──►│
  │                   │                    │                   │ refund to card
  │                   │                    │ releaseSeats()    │
  │                   │                    │ status = CANCELLED│
  │◄──────────────────│                    │                   │
  │ 200 OK             │                    │                   │
  │ {refundAmount,     │                    │                   │
  │  refundedAt}       │                    │                   │
```

---

## 3. Database Schema

```sql
-- Booking
CREATE TABLE bookings (
    id            BIGSERIAL PRIMARY KEY,
    pnr_code      CHAR(6) UNIQUE NOT NULL,       -- VD: AB3X9K
    user_id       BIGINT NOT NULL REFERENCES users(id),
    status        VARCHAR(20) DEFAULT 'PENDING', -- PENDING|CONFIRMED|CANCELLED|EXPIRED|COMPLETED
    trip_type     VARCHAR(20) DEFAULT 'ONE_WAY', -- ONE_WAY | ROUND_TRIP
    subtotal      DECIMAL(12,2) NOT NULL,        -- Tổng tiền vé
    service_fee   DECIMAL(12,2) DEFAULT 0,       -- Phí dịch vụ
    total_price   DECIMAL(12,2) NOT NULL,        -- Tổng thanh toán
    currency      CHAR(3) DEFAULT 'VND',
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20),
    expires_at    TIMESTAMP,                     -- NULL khi đã confirm
    confirmed_at  TIMESTAMP,
    cancelled_at  TIMESTAMP,
    cancel_reason TEXT,
    refund_amount DECIMAL(12,2),
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

-- Chi tiết booking (1 booking có thể có chuyến đi + chuyến về)
CREATE TABLE booking_segments (
    id               BIGSERIAL PRIMARY KEY,
    booking_id       BIGINT NOT NULL REFERENCES bookings(id),
    flight_class_id  BIGINT NOT NULL REFERENCES flight_classes(id),
    segment_type     VARCHAR(20) DEFAULT 'OUTBOUND', -- OUTBOUND | RETURN
    segment_price    DECIMAL(12,2) NOT NULL,
    created_at       TIMESTAMP DEFAULT NOW()
);

-- Hành khách
CREATE TABLE passengers (
    id                BIGSERIAL PRIMARY KEY,
    booking_segment_id BIGINT NOT NULL REFERENCES booking_segments(id),
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    date_of_birth     DATE NOT NULL,
    gender            VARCHAR(10),
    nationality       CHAR(2) NOT NULL,          -- VN, US, JP
    id_type           VARCHAR(20) NOT NULL,      -- CCCD | PASSPORT
    id_number         VARCHAR(50) NOT NULL,
    id_expiry         DATE,
    passenger_type    VARCHAR(10) DEFAULT 'ADULT', -- ADULT|CHILD|INFANT
    seat_id           BIGINT REFERENCES seats(id), -- NULL với INFANT
    extra_baggage_kg  INT DEFAULT 0,
    meal_preference   VARCHAR(20),               -- STANDARD|VEGETARIAN|HALAL
    created_at        TIMESTAMP DEFAULT NOW()
);

-- Index
CREATE INDEX idx_bookings_pnr ON bookings(pnr_code);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_status_expires ON bookings(status, expires_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_passengers_id_number ON passengers(id_number);
```

---

## 4. API Specification

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| POST | `/api/v1/bookings` | USER | Tạo booking mới |
| GET | `/api/v1/bookings/{pnr}` | USER | Chi tiết booking |
| GET | `/api/v1/bookings/my` | USER | Lịch sử booking của tôi |
| DELETE | `/api/v1/bookings/{pnr}` | USER | Hủy booking |
| GET | `/api/v1/flights/{id}/seats` | USER | Xem sơ đồ ghế |
| PATCH | `/api/v1/bookings/{pnr}/seats` | USER | Đổi ghế (phí 50k) |

### Request mẫu — Tạo Booking

```json
POST /api/v1/bookings
{
  "flightClassId": 201,
  "contactEmail": "nguyenvana@gmail.com",
  "contactPhone": "0901234567",
  "passengers": [
    {
      "firstName": "Van A",
      "lastName": "Nguyen",
      "dateOfBirth": "1990-05-15",
      "gender": "MALE",
      "nationality": "VN",
      "idType": "CCCD",
      "idNumber": "012345678901",
      "passengerType": "ADULT",
      "seatId": 1055,
      "mealPreference": "STANDARD"
    },
    {
      "firstName": "Thi B",
      "lastName": "Tran",
      "dateOfBirth": "1993-08-20",
      "gender": "FEMALE",
      "nationality": "VN",
      "idType": "CCCD",
      "idNumber": "098765432100",
      "passengerType": "ADULT",
      "seatId": 1056
    }
  ]
}
```

### Response 201 — Booking Created

```json
{
  "pnrCode": "AB3X9K",
  "status": "PENDING",
  "expiresAt": "2025-06-10T14:30:00",
  "flight": {
    "flightNumber": "VJ123",
    "from": "SGN", "to": "HAN",
    "departureTime": "2025-06-15T06:00:00"
  },
  "passengers": [
    { "name": "NGUYEN VAN A", "seat": "14A", "idNumber": "012345678901" },
    { "name": "TRAN THI B", "seat": "14B", "idNumber": "098765432100" }
  ],
  "pricing": {
    "subtotal": 1500000,
    "serviceFee": 55000,
    "totalPrice": 1555000,
    "currency": "VND"
  },
  "paymentDeadline": "2025-06-10T14:30:00"
}
```

---

## 5. Code mẫu

### BookingService — Tạo booking với Pessimistic Lock

```java
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final FlightClassRepository flightClassRepository;
    private final SeatRepository seatRepository;
    private final PassengerRepository passengerRepository;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, Long userId) {

        // ① Lấy flight class với PESSIMISTIC LOCK (chống race condition)
        FlightClass flightClass = flightClassRepository
                .findByIdWithLock(request.getFlightClassId())
                .orElseThrow(() -> new NotFoundException("Hạng vé không tồn tại"));

        int passengerCount = request.getNonInfantPassengers();

        // ② Kiểm tra đủ ghế
        if (flightClass.getAvailableSeats() < passengerCount) {
            throw new NotEnoughSeatsException(
                "Chỉ còn " + flightClass.getAvailableSeats() + " ghế trống"
            );
        }

        // ③ Kiểm tra ghế được chọn còn trống
        List<Long> seatIds = request.getSelectedSeatIds();
        List<Seat> seats = seatRepository.findAllByIdWithLock(seatIds);
        seats.forEach(seat -> {
            if (!seat.isAvailable()) {
                throw new SeatUnavailableException("Ghế " + seat.getSeatNumber() + " đã được chọn");
            }
        });

        // ④ Kiểm tra trùng CCCD trong cùng chuyến bay
        List<String> idNumbers = request.getPassengerIdNumbers();
        if (passengerRepository.existsDuplicateOnFlight(flightClass.getFlight().getId(), idNumbers)) {
            throw new DuplicatePassengerException("Một hành khách đã có booking trên chuyến bay này");
        }

        // ⑤ Tạo Booking
        Booking booking = Booking.builder()
                .pnrCode(generatePNR())
                .user(User.builder().id(userId).build())
                .status(BookingStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .build();
        booking = bookingRepository.save(booking);

        // ⑥ Tạo segment và passengers
        BookingSegment segment = createSegment(booking, flightClass, request);
        createPassengers(segment, request.getPassengers(), seats);

        // ⑦ Đánh dấu ghế là đã giữ
        seatRepository.markAsHeld(seatIds);

        // ⑧ Giảm available_seats
        flightClass.setAvailableSeats(flightClass.getAvailableSeats() - passengerCount);
        flightClassRepository.save(flightClass);

        // ⑨ Tính giá
        calculatePricing(booking, flightClass, request);
        booking = bookingRepository.save(booking);

        return bookingMapper.toResponse(booking);
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
        } while (bookingRepository.existsByPnrCode(pnr)); // Đảm bảo unique
        return pnr;
    }
}
```

### Scheduled Job — Hủy booking hết hạn

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Scheduled(fixedDelay = 60_000) // Mỗi 1 phút
    @Transactional
    public void cancelExpiredBookings() {
        List<Booking> expired = bookingRepository.findExpiredPending(LocalDateTime.now());

        if (!expired.isEmpty()) {
            log.info("Cancelling {} expired bookings", expired.size());
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

### Seat Map — API trả về sơ đồ ghế

```java
public SeatMapResponse getSeatMap(Long flightId) {
    List<Seat> allSeats = seatRepository.findByFlightId(flightId);

    // Group theo hạng ghế và hàng
    Map<String, Map<Integer, List<Seat>>> grouped = allSeats.stream()
        .collect(Collectors.groupingBy(
            Seat::getClassType,
            Collectors.groupingBy(Seat::getRowNumber)
        ));

    return SeatMapResponse.builder()
        .firstClass(buildSection(grouped.get("FIRST_CLASS")))
        .business(buildSection(grouped.get("BUSINESS")))
        .economy(buildSection(grouped.get("ECONOMY")))
        .legend(buildLegend())
        .build();
}
```

**Response sơ đồ ghế:**
```json
{
  "economy": {
    "rows": [
      {
        "rowNumber": 14,
        "isExitRow": true,
        "seats": [
          { "seatNumber": "14A", "position": "WINDOW", "isAvailable": true, "extraFee": 150000, "isExtraLegroom": true },
          { "seatNumber": "14B", "position": "MIDDLE", "isAvailable": false, "extraFee": 150000 },
          { "seatNumber": "14C", "position": "AISLE",  "isAvailable": true, "extraFee": 150000 },
          { "seatNumber": "14D", "position": "AISLE",  "isAvailable": true, "extraFee": 150000 },
          { "seatNumber": "14E", "position": "MIDDLE", "isAvailable": true, "extraFee": 150000 },
          { "seatNumber": "14F", "position": "WINDOW", "isAvailable": true, "extraFee": 150000 }
        ]
      }
    ]
  }
}
```
