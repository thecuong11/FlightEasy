# Service 02 — Quản lý chuyến bay (Flight Management)

> **Module:** Flight Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P0 — Dữ liệu nền tảng cho toàn bộ hệ thống đặt vé

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Flight Management quản lý toàn bộ dữ liệu về sân bay, hãng bay và chuyến bay. Chỉ Admin mới có quyền tạo/sửa/xóa. Dữ liệu này là nguồn cho module Tìm kiếm và Booking.

### 1.2 Các thực thể chính

| Thực thể | Mô tả |
|----------|-------|
| **Airport** | Sân bay: mã IATA, tên, thành phố, múi giờ |
| **Airline** | Hãng bay: tên, mã IATA, quốc gia |
| **Aircraft** | Loại máy bay: Boeing 787, Airbus A320 — xác định sơ đồ ghế |
| **Flight** | Chuyến bay cụ thể: hãng, điểm đi/đến, giờ bay, trạng thái |
| **FlightClass** | Hạng vé của mỗi chuyến: Economy/Business/First — giá và số ghế |
| **Seat** | Ghế cụ thể: số ghế, hạng, loại (cửa sổ/lối đi/giữa) |

### 1.3 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Mã IATA sân bay gồm đúng 3 ký tự in hoa (VD: SGN, HAN, DAD) |
| BR-02 | Mã IATA hãng bay gồm đúng 2 ký tự (VD: VN, VJ, QH) |
| BR-03 | Giờ đến phải sau giờ khởi hành |
| BR-04 | Không được xóa sân bay/hãng bay đang có chuyến bay trong tương lai |
| BR-05 | Chuyến bay chỉ có thể hủy nếu trạng thái là SCHEDULED hoặc DELAYED |
| BR-06 | Khi hủy chuyến bay, toàn bộ booking liên quan phải được thông báo và hoàn tiền |
| BR-07 | Số ghế available không được âm |
| BR-08 | Mã chuyến bay (flight number) phải unique trong cùng ngày |
| BR-09 | Chuyến bay quá khứ không được sửa trạng thái (trừ Admin cấp cao) |

### 1.4 Vòng đời trạng thái chuyến bay

```
SCHEDULED ──► BOARDING ──► DEPARTED ──► ARRIVED
     │                                      
     ├──────────────────────────────────► CANCELLED
     │
     └──► DELAYED ──► BOARDING ──► DEPARTED ──► ARRIVED
                │
                └──────────────────────────────► CANCELLED
```

| Trạng thái | Ý nghĩa |
|-----------|---------|
| `SCHEDULED` | Đã lên lịch, bán vé bình thường |
| `DELAYED` | Trễ giờ — cập nhật giờ khởi hành mới |
| `BOARDING` | Đang lên máy bay — không cho đặt thêm |
| `DEPARTED` | Đã cất cánh |
| `ARRIVED` | Đã hạ cánh |
| `CANCELLED` | Đã hủy — trigger hoàn tiền |

---

## 2. Flow Diagram

### 2.1 Flow Tạo chuyến bay (Admin)

```
Admin                FlightController       FlightService         DB
  │                       │                     │                  │
  │ POST /admin/flights    │                     │                  │
  │ {flightData}           │                     │                  │
  │──────────────────────►│                     │                  │
  │                       │ [Check ROLE_ADMIN]  │                  │
  │                       │ createFlight()      │                  │
  │                       │────────────────────►│                  │
  │                       │                     │ validate()       │
  │                       │                     │ - giờ đến > giờ đi?
  │                       │                     │ - sân bay tồn tại?
  │                       │                     │ - flight number unique?
  │                       │                     │                  │
  │                       │                     │ saveFlight()     │
  │                       │                     │─────────────────►│
  │                       │                     │ createSeats()    │
  │                       │                     │ (theo Aircraft   │
  │                       │                     │  template)       │
  │                       │                     │─────────────────►│
  │◄──────────────────────│                     │                  │
  │ 201 Created            │                     │                  │
  │ {flight + classes}     │                     │                  │
```

### 2.2 Flow Cập nhật trạng thái (Admin)

```
Admin                FlightController       FlightService      NotificationService
  │                       │                     │                     │
  │ PATCH /admin/          │                     │                     │
  │ flights/{id}/status    │                     │                     │
  │ {status: CANCELLED}    │                     │                     │
  │──────────────────────►│                     │                     │
  │                       │ updateStatus()      │                     │
  │                       │────────────────────►│                     │
  │                       │                     │ validateTransition()│
  │                       │                     │ (SCHEDULED → CANCELLED ✓)
  │                       │                     │                     │
  │                       │                     │ [Nếu CANCELLED]     │
  │                       │                     │ getAffectedBookings()
  │                       │                     │ triggerRefunds()    │
  │                       │                     │ notifyPassengers() ─►│
  │                       │                     │                     │ sendEmail()
  │◄──────────────────────│                     │                     │
  │ 200 OK                 │                     │                     │
```

---

## 3. Database Schema

```sql
-- Sân bay
CREATE TABLE airports (
    id           BIGSERIAL PRIMARY KEY,
    iata_code    CHAR(3) UNIQUE NOT NULL,    -- SGN, HAN, DAD
    icao_code    CHAR(4),                    -- VVTS, VVNB
    name         VARCHAR(255) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    country      VARCHAR(100) NOT NULL,
    country_code CHAR(2) NOT NULL,           -- VN, TH, SG
    timezone     VARCHAR(50) NOT NULL,       -- Asia/Ho_Chi_Minh
    latitude     DECIMAL(9,6),
    longitude    DECIMAL(9,6),
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT NOW()
);

-- Hãng bay
CREATE TABLE airlines (
    id           BIGSERIAL PRIMARY KEY,
    iata_code    CHAR(2) UNIQUE NOT NULL,    -- VN, VJ
    icao_code    CHAR(3),                    -- HVN, VJC
    name         VARCHAR(255) NOT NULL,
    country      VARCHAR(100),
    logo_url     TEXT,
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT NOW()
);

-- Loại máy bay (xác định số ghế và layout)
CREATE TABLE aircraft_types (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20) UNIQUE NOT NULL, -- B787, A320, A321
    name         VARCHAR(255) NOT NULL,
    total_seats  INT NOT NULL,
    economy_seats    INT NOT NULL,
    business_seats   INT DEFAULT 0,
    first_class_seats INT DEFAULT 0
);

-- Chuyến bay
CREATE TABLE flights (
    id               BIGSERIAL PRIMARY KEY,
    flight_number    VARCHAR(10) NOT NULL,        -- VN123
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
    UNIQUE (flight_number, departure_time::DATE)
);

-- Hạng vé của chuyến bay (giá + số ghế còn lại)
CREATE TABLE flight_classes (
    id               BIGSERIAL PRIMARY KEY,
    flight_id        BIGINT NOT NULL REFERENCES flights(id),
    class_type       VARCHAR(20) NOT NULL,   -- ECONOMY, BUSINESS, FIRST_CLASS
    base_price       DECIMAL(12,2) NOT NULL,
    total_seats      INT NOT NULL,
    available_seats  INT NOT NULL,
    baggage_allowance_kg INT DEFAULT 20,
    carry_on_kg      INT DEFAULT 7,
    is_refundable    BOOLEAN DEFAULT TRUE,
    refund_fee_percent INT DEFAULT 0,

    CONSTRAINT chk_seats CHECK (available_seats >= 0),
    UNIQUE (flight_id, class_type)
);

-- Ghế cụ thể
CREATE TABLE seats (
    id             BIGSERIAL PRIMARY KEY,
    flight_id      BIGINT NOT NULL REFERENCES flights(id),
    seat_number    VARCHAR(5) NOT NULL,       -- 12A, 12B, 12C
    class_type     VARCHAR(20) NOT NULL,
    position       VARCHAR(10) NOT NULL,      -- WINDOW, MIDDLE, AISLE
    row_number     INT NOT NULL,
    is_available   BOOLEAN DEFAULT TRUE,
    is_extra_legroom BOOLEAN DEFAULT FALSE,
    extra_fee      DECIMAL(10,2) DEFAULT 0,

    UNIQUE (flight_id, seat_number)
);

-- Index
CREATE INDEX idx_flights_departure ON flights(departure_time);
CREATE INDEX idx_flights_origin_dest ON flights(origin_id, destination_id);
CREATE INDEX idx_flights_status ON flights(status);
CREATE INDEX idx_seats_flight ON seats(flight_id, is_available);
```

---

## 4. API Specification

### Sân bay (Airport)

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/v1/airports` | Public | Danh sách sân bay |
| GET | `/api/v1/airports/{iata}` | Public | Chi tiết sân bay |
| POST | `/api/v1/admin/airports` | ADMIN | Tạo sân bay |
| PUT | `/api/v1/admin/airports/{id}` | ADMIN | Cập nhật sân bay |
| DELETE | `/api/v1/admin/airports/{id}` | ADMIN | Xóa (soft delete) |

### Chuyến bay (Flight)

| Method | Endpoint | Auth | Mô tả |
|--------|----------|------|-------|
| GET | `/api/v1/flights/{id}` | Public | Chi tiết chuyến bay |
| GET | `/api/v1/flights/{id}/seats` | User | Sơ đồ ghế |
| POST | `/api/v1/admin/flights` | ADMIN | Tạo chuyến bay |
| PUT | `/api/v1/admin/flights/{id}` | ADMIN | Cập nhật chuyến bay |
| PATCH | `/api/v1/admin/flights/{id}/status` | ADMIN | Cập nhật trạng thái |
| DELETE | `/api/v1/admin/flights/{id}` | ADMIN | Xóa chuyến bay |

### Request mẫu — Tạo chuyến bay

```json
POST /api/v1/admin/flights
{
  "flightNumber": "VN123",
  "airlineId": 1,
  "aircraftTypeId": 2,
  "originIata": "SGN",
  "destinationIata": "HAN",
  "departureTime": "2025-06-15T08:00:00",
  "arrivalTime": "2025-06-15T10:00:00",
  "terminal": "T1",
  "gate": "A12",
  "classes": [
    {
      "classType": "ECONOMY",
      "basePrice": 850000,
      "totalSeats": 150,
      "baggageAllowanceKg": 23,
      "isRefundable": true,
      "refundFeePercent": 30
    },
    {
      "classType": "BUSINESS",
      "basePrice": 3500000,
      "totalSeats": 16,
      "baggageAllowanceKg": 32,
      "isRefundable": true,
      "refundFeePercent": 10
    }
  ]
}
```

### Response mẫu — Chi tiết chuyến bay

```json
{
  "id": 1,
  "flightNumber": "VN123",
  "airline": { "code": "VN", "name": "Vietnam Airlines", "logoUrl": "..." },
  "origin": { "iata": "SGN", "name": "Tân Sơn Nhất", "city": "Hồ Chí Minh" },
  "destination": { "iata": "HAN", "name": "Nội Bài", "city": "Hà Nội" },
  "departureTime": "2025-06-15T08:00:00",
  "arrivalTime": "2025-06-15T10:00:00",
  "durationMinutes": 120,
  "status": "SCHEDULED",
  "terminal": "T1",
  "gate": "A12",
  "classes": [
    {
      "classType": "ECONOMY",
      "basePrice": 850000,
      "availableSeats": 142,
      "totalSeats": 150,
      "baggageAllowanceKg": 23
    }
  ]
}
```

---

## 5. Code mẫu

### FlightService — Tạo chuyến bay và auto-generate ghế

```java
@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final SeatRepository seatRepository;
    private final AircraftTypeRepository aircraftTypeRepository;

    @Transactional
    public FlightResponse createFlight(CreateFlightRequest request) {
        // Validate business rules
        if (!request.getArrivalTime().isAfter(request.getDepartureTime())) {
            throw new InvalidFlightException("Giờ đến phải sau giờ khởi hành");
        }
        if (request.getOriginId().equals(request.getDestinationId())) {
            throw new InvalidFlightException("Điểm đi và điểm đến không được trùng nhau");
        }

        Flight flight = flightMapper.toEntity(request);
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setDurationMinutes(
            (int) Duration.between(request.getDepartureTime(), request.getArrivalTime()).toMinutes()
        );
        flight = flightRepository.save(flight);

        // Tự động tạo ghế dựa theo Aircraft Type
        generateSeats(flight);

        return flightMapper.toResponse(flight);
    }

    private void generateSeats(Flight flight) {
        AircraftType aircraft = flight.getAircraftType();
        List<Seat> seats = new ArrayList<>();
        int rowNum = 1;

        // First Class: hàng 1-2, 2 ghế/hàng (A, C)
        for (int i = 0; i < aircraft.getFirstClassSeats() / 2; i++) {
            seats.add(buildSeat(flight, rowNum, "A", "FIRST_CLASS", "WINDOW", false));
            seats.add(buildSeat(flight, rowNum, "C", "FIRST_CLASS", "AISLE", false));
            rowNum++;
        }

        // Business: hàng 3-8, 4 ghế/hàng (A, C, D, F)
        for (int i = 0; i < aircraft.getBusinessSeats() / 4; i++) {
            seats.add(buildSeat(flight, rowNum, "A", "BUSINESS", "WINDOW", false));
            seats.add(buildSeat(flight, rowNum, "C", "BUSINESS", "AISLE", false));
            seats.add(buildSeat(flight, rowNum, "D", "BUSINESS", "AISLE", false));
            seats.add(buildSeat(flight, rowNum, "F", "BUSINESS", "WINDOW", false));
            rowNum++;
        }

        // Economy: 6 ghế/hàng (A, B, C, D, E, F)
        // Hàng exit (14, 15): extra legroom
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

    @Transactional
    public void updateFlightStatus(Long flightId, FlightStatus newStatus) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException(flightId));

        validateStatusTransition(flight.getStatus(), newStatus);
        flight.setStatus(newStatus);
        flightRepository.save(flight);

        // Trigger side effects
        if (newStatus == FlightStatus.CANCELLED) {
            eventPublisher.publishEvent(new FlightCancelledEvent(flight));
        }
    }

    private void validateStatusTransition(FlightStatus current, FlightStatus next) {
        Map<FlightStatus, Set<FlightStatus>> allowed = Map.of(
            FlightStatus.SCHEDULED, Set.of(FlightStatus.BOARDING, FlightStatus.DELAYED, FlightStatus.CANCELLED),
            FlightStatus.DELAYED,   Set.of(FlightStatus.BOARDING, FlightStatus.CANCELLED),
            FlightStatus.BOARDING,  Set.of(FlightStatus.DEPARTED),
            FlightStatus.DEPARTED,  Set.of(FlightStatus.ARRIVED)
        );
        if (!allowed.getOrDefault(current, Set.of()).contains(next)) {
            throw new InvalidStatusTransitionException(current, next);
        }
    }
}
```
