# FlightEasy — Sơ đồ quan hệ giữa các bảng (ERD)

> Tổng hợp từ toàn bộ 8 module: Auth, Flight Management, Flight Search, Booking & Seat, Payment (VNPay), Email Notification, Admin Dashboard, Token Blacklist.

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "có"
    USERS ||--o{ PASSWORD_RESET_TOKENS : "có"
    USERS ||--o{ BOOKINGS : "đặt"
    USERS ||--o{ PAYMENTS : "hoàn tiền bởi"
    USERS ||--o{ ADMIN_AUDIT_LOGS : "thực hiện (admin)"

    AIRLINES ||--o{ FLIGHTS : "khai thác"
    AIRCRAFT_TYPES ||--o{ FLIGHTS : "sử dụng"
    AIRPORTS ||--o{ FLIGHTS : "điểm đi (origin)"
    AIRPORTS ||--o{ FLIGHTS : "điểm đến (destination)"

    FLIGHTS ||--o{ FLIGHT_CLASSES : "có hạng vé"
    FLIGHTS ||--o{ SEATS : "có ghế"

    FLIGHT_CLASSES ||--o{ BOOKING_SEGMENTS : "được đặt trong"
    SEATS ||--o{ PASSENGERS : "gán cho"

    BOOKINGS ||--o{ BOOKING_SEGMENTS : "gồm"
    BOOKING_SEGMENTS ||--o{ PASSENGERS : "gồm hành khách"
    BOOKINGS ||--o{ PAYMENTS : "được thanh toán"

    USERS {
        bigint id PK
        varchar email UK
        varchar password
        varchar full_name
        varchar phone
        varchar role
        varchar provider
        boolean is_active
        int failed_attempts
        timestamp locked_until
    }

    REFRESH_TOKENS {
        bigint id PK
        varchar token UK
        bigint user_id FK
        varchar device_info
        varchar ip_address
        boolean is_used
        timestamp expires_at
    }

    PASSWORD_RESET_TOKENS {
        bigint id PK
        varchar token UK
        bigint user_id FK
        boolean is_used
        timestamp expires_at
    }

    AIRPORTS {
        bigint id PK
        char iata_code UK
        char icao_code
        varchar name
        varchar city
        varchar country
        boolean is_active
    }

    AIRLINES {
        bigint id PK
        char iata_code UK
        char icao_code
        varchar name
        varchar country
        boolean is_active
    }

    AIRCRAFT_TYPES {
        bigint id PK
        varchar code UK
        varchar name
        int total_seats
        int economy_seats
        int business_seats
        int first_class_seats
    }

    FLIGHTS {
        bigint id PK
        varchar flight_number
        bigint airline_id FK
        bigint aircraft_type_id FK
        bigint origin_id FK
        bigint destination_id FK
        timestamp departure_time
        timestamp arrival_time
        int duration_minutes
        varchar status
        int delay_minutes
    }

    FLIGHT_CLASSES {
        bigint id PK
        bigint flight_id FK
        varchar class_type
        decimal base_price
        int total_seats
        int available_seats
        boolean is_refundable
    }

    SEATS {
        bigint id PK
        bigint flight_id FK
        varchar seat_number
        varchar class_type
        varchar position
        int row_number
        boolean is_available
        boolean is_extra_legroom
    }

    BOOKINGS {
        bigint id PK
        char pnr_code UK
        bigint user_id FK
        varchar status
        varchar trip_type
        decimal subtotal
        decimal total_price
        varchar contact_email
        timestamp expires_at
        timestamp confirmed_at
        timestamp cancelled_at
    }

    BOOKING_SEGMENTS {
        bigint id PK
        bigint booking_id FK
        bigint flight_class_id FK
        varchar segment_type
        decimal segment_price
    }

    PASSENGERS {
        bigint id PK
        bigint booking_segment_id FK
        varchar first_name
        varchar last_name
        date date_of_birth
        char nationality
        varchar id_type
        varchar id_number
        varchar passenger_type
        bigint seat_id FK
        int extra_baggage_kg
    }

    PAYMENTS {
        bigint id PK
        bigint booking_id FK
        decimal amount
        bigint amount_vnpay
        varchar gateway
        varchar status
        varchar vnp_txn_ref UK
        varchar vnp_transaction_no
        varchar vnp_response_code
        decimal refund_amount
        bigint refunded_by FK
        timestamp refunded_at
    }

    EMAIL_LOGS {
        bigint id PK
        varchar recipient
        varchar subject
        varchar template_name
        varchar reference_id "PNR hoặc user_id, không FK cứng"
        varchar status
        int attempts
        timestamp next_retry_at
        timestamp sent_at
    }

    ADMIN_AUDIT_LOGS {
        bigint id PK
        bigint admin_id FK
        varchar action
        varchar entity_type
        varchar entity_id
        text old_value
        text new_value
        varchar ip_address
    }
```

---

## Giải thích quan hệ chính

### 1. Nhóm Auth (Service 01, 08)
- `users` 1—n `refresh_tokens`: mỗi user có nhiều refresh token (theo thiết bị/phiên đăng nhập).
- `users` 1—n `password_reset_tokens`: token quên mật khẩu.
- **Token Blacklist** (Service 08) không có bảng riêng — dùng Redis (key `blacklist:token:<accessToken>`), không thuộc quan hệ DB quan hệ.

### 2. Nhóm Flight Management (Service 02, 03)
- `airlines`, `aircraft_types`, `airports` là bảng danh mục (master data).
- `flights` tham chiếu tới cả 4 bảng trên: `airline_id`, `aircraft_type_id`, `origin_id` và `destination_id` (2 FK cùng trỏ về `airports`).
- `flights` 1—n `flight_classes` (mỗi chuyến bay có nhiều hạng vé: Economy/Business/First).
- `flights` 1—n `seats` (sơ đồ ghế của từng chuyến bay).

### 3. Nhóm Booking & Seat (Service 04)
- `bookings` 1—n `booking_segments` (một booking có thể gồm chặng đi + chặng về — khứ hồi).
- `booking_segments` n—1 `flight_classes` (mỗi segment gắn với 1 hạng vé của 1 chuyến bay cụ thể).
- `booking_segments` 1—n `passengers` (nhiều hành khách trong cùng 1 segment).
- `passengers` n—1 `seats` (mỗi hành khách được gán 1 ghế, trừ hành khách INFANT có thể NULL).

### 4. Nhóm Payment (Service 05)
- `bookings` 1—n `payments` (có thể có nhiều lần thử thanh toán, nhưng chỉ 1 `PENDING` tại một thời điểm — theo fix trùng lặp gần đây).
- `payments.refunded_by` → `users.id` (admin thực hiện hoàn tiền).

### 5. Nhóm Email (Service 06)
- `email_logs` **không có FK cứng** — `reference_id` là chuỗi tự do (PNR code hoặc user ID) để linh hoạt log cho nhiều loại sự kiện (xác nhận booking, nhắc check-in...).

### 6. Nhóm Admin (Service 07)
- `admin_audit_logs.admin_id` → `users.id`: ghi lại hành động của admin (dùng AOP `@AfterReturning` tự động log).
- Ngoài ra còn 2 **view** tổng hợp không phải bảng gốc:
  - `daily_revenue` (materialized view) — tổng hợp doanh thu theo ngày từ `bookings`.
  - `revenue_by_route` (view) — join `bookings → booking_segments → flight_classes → flights → airports/airlines`.

---

## Tổng hợp Foreign Key

| Bảng | Cột FK | Tham chiếu tới |
|---|---|---|
| refresh_tokens | user_id | users.id |
| password_reset_tokens | user_id | users.id |
| flights | airline_id | airlines.id |
| flights | aircraft_type_id | aircraft_types.id |
| flights | origin_id | airports.id |
| flights | destination_id | airports.id |
| flight_classes | flight_id | flights.id |
| seats | flight_id | flights.id |
| bookings | user_id | users.id |
| booking_segments | booking_id | bookings.id |
| booking_segments | flight_class_id | flight_classes.id |
| passengers | booking_segment_id | booking_segments.id |
| passengers | seat_id | seats.id |
| payments | booking_id | bookings.id |
| payments | refunded_by | users.id |
| admin_audit_logs | admin_id | users.id |
