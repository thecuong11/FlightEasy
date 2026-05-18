# Hướng dẫn Test Postman Chi Tiết — FlightEasy

> **Base URL:** `http://localhost:8080`  
> **Content-Type:** `application/json` (trừ khi ghi khác)

---

## Chuẩn bị trước khi test

### 1. Biến môi trường Postman

Tạo Environment trong Postman với các biến:

| Variable | Initial Value | Ghi chú |
|----------|--------------|---------|
| `base_url` | `http://localhost:8080` | |
| `access_token` | _(để trống)_ | Tự điền sau khi login |
| `refresh_token` | _(để trống)_ | Tự điền sau khi login |
| `admin_token` | _(để trống)_ | Tự điền sau khi login admin |
| `pnr_code` | _(để trống)_ | Tự điền sau khi tạo booking |

### 2. Script tự động lưu token

Trong tab **Tests** của request Login, thêm script:

```javascript
const res = pm.response.json();
pm.environment.set("access_token", res.accessToken);
pm.environment.set("refresh_token", res.refreshToken);
```

### 3. Thứ tự chạy SQL trước khi test

```sql
INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');
INSERT INTO airlines (iata_code, name, is_active) VALUES ('VN', 'Vietnam Airlines', true), ('VJ', 'VietJet Air', true);
INSERT INTO airports (iata_code, name, city, country, country_code, timezone, is_active)
VALUES ('SGN', 'Tân Sơn Nhất', 'Hồ Chí Minh', 'Việt Nam', 'VN', 'Asia/Ho_Chi_Minh', true),
       ('HAN', 'Nội Bài', 'Hà Nội', 'Việt Nam', 'VN', 'Asia/Ho_Chi_Minh', true);
INSERT INTO aircraft_types (code, name, total_seats, economy_seats, business_seats, first_class_seats)
VALUES ('A321', 'Airbus A321', 220, 196, 24, 0);
```

---

## MODULE 01 — Auth Service

### 1.1 Đăng ký tài khoản User

```
POST {{base_url}}/api/auth/register
```

**Body (thành công):**
```json
{
  "username": "testuser",
  "email": "testuser@gmail.com",
  "password": "Test@123456",
  "fullName": "Nguyen Van Test"
}
```
**Kết quả mong đợi:** `200 OK`
```json
"Đăng ký thành công"
```

---

**Body (email đã tồn tại):**
```json
{
  "username": "testuser2",
  "email": "testuser@gmail.com",
  "password": "Test@123456",
  "fullName": "Nguyen Van Test 2"
}
```
**Kết quả mong đợi:** `409 Conflict`
```json
{
  "code": "DUPLICATE",
  "message": "Email đã tồn tại"
}
```

---

**Body (thiếu field):**
```json
{
  "email": "testuser@gmail.com"
}
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "VALIDATION",
  "errors": {
    "password": "Password không được để trống"
  }
}
```

---

### 1.2 Đăng ký tài khoản Admin

```
POST {{base_url}}/api/auth/register
```

```json
{
  "username": "admin",
  "email": "admin@fighteasy.vn",
  "password": "Admin@123456",
  "fullName": "Admin FlightEasy",
  "roles": ["ROLE_ADMIN"]
}
```
**Kết quả mong đợi:** `200 OK`

---

### 1.3 Đăng nhập

```
POST {{base_url}}/api/auth/login
```

**Body (thành công):**
```json
{
  "email": "testuser@gmail.com",
  "password": "Test@123456"
}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716...",
  "tokenType": "Bearer"
}
```
> Lưu `accessToken` vào biến `access_token`, `refreshToken` vào `refresh_token`

---

**Body (sai mật khẩu):**
```json
{
  "email": "testuser@gmail.com",
  "password": "SaiMatKhau123"
}
```
**Kết quả mong đợi:** `401 Unauthorized`
```json
{
  "message": "Email hoặc mật khẩu không đúng"
}
```

---

**Body (sai mật khẩu 5 lần liên tiếp — tài khoản bị khóa):**
```json
{
  "email": "testuser@gmail.com",
  "password": "SaiMatKhau123"
}
```
**Kết quả lần 5:** `423 Locked`
```json
{
  "message": "Tài khoản bị khóa đến 2025-07-01T10:30:00"
}
```

---

### 1.4 Refresh Token

```
POST {{base_url}}/api/auth/refresh
```

**Body:**
```json
{
  "refreshToken": "{{refresh_token}}"
}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...(mới)",
  "refreshToken": "550e8400-e29b-41d4-a716...",
  "tokenType": "Bearer"
}
```

---

**Body (refresh token không tồn tại):**
```json
{
  "refreshToken": "token-gia-mao-khong-ton-tai"
}
```
**Kết quả mong đợi:** `401 Unauthorized`
```json
{
  "message": "Refresh token không tồn tại"
}
```

---

### 1.5 Đăng xuất

```
POST {{base_url}}/api/auth/logout
Authorization: Bearer {{access_token}}
```

**Body:**
```json
{
  "refreshToken": "{{refresh_token}}"
}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "message": "Đăng xuất thành công"
}
```

---

### 1.6 Gọi API sau khi logout

```
GET {{base_url}}/api/v1/flights/1
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `401 Unauthorized`
```json
{
  "message": "Token đã bị thu hồi, vui lòng đăng nhập lại"
}
```

---

## MODULE 02 — Flight Management

> **Yêu cầu:** Dùng `admin_token` (đăng nhập bằng tài khoản Admin)

### 2.1 Tạo sân bay

```
POST {{base_url}}/api/v1/admin/airports
Authorization: Bearer {{admin_token}}
```

**Body (thành công):**
```json
{
  "iataCode": "DAD",
  "icaoCode": "VVDN",
  "name": "Sân bay Quốc tế Đà Nẵng",
  "city": "Đà Nẵng",
  "country": "Việt Nam",
  "countryCode": "VN",
  "timezone": "Asia/Ho_Chi_Minh",
  "latitude": 16.043917,
  "longitude": 108.199261,
  "isActive": true
}
```
**Kết quả mong đợi:** `201 Created`
```json
{
  "id": 3,
  "iataCode": "DAD",
  "name": "Sân bay Quốc tế Đà Nẵng",
  "city": "Đà Nẵng"
}
```

---

**Body (IATA code đã tồn tại):**
```json
{
  "iataCode": "SGN",
  "name": "Tân Sơn Nhất Duplicate",
  "city": "Hồ Chí Minh",
  "country": "Việt Nam",
  "countryCode": "VN",
  "timezone": "Asia/Ho_Chi_Minh"
}
```
**Kết quả mong đợi:** `409 Conflict`
```json
{
  "code": "DUPLICATE",
  "message": "Mã IATA đã tồn tại: SGN"
}
```

---

### 2.2 Tạo chuyến bay

```
POST {{base_url}}/api/v1/admin/flights
Authorization: Bearer {{admin_token}}
```

**Body (thành công — có aircraft type):**
```json
{
  "flightNumber": "VN123",
  "airlineId": 1,
  "aircraftTypeId": 1,
  "originIata": "SGN",
  "destinationIata": "HAN",
  "departureTime": "2025-07-15T08:00:00",
  "arrivalTime": "2025-07-15T10:10:00",
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
**Kết quả mong đợi:** `201 Created` + flight object với classes

---

**Body (giờ đến trước giờ đi):**
```json
{
  "flightNumber": "VN999",
  "airlineId": 1,
  "originIata": "SGN",
  "destinationIata": "HAN",
  "departureTime": "2025-07-15T10:00:00",
  "arrivalTime": "2025-07-15T08:00:00",
  "classes": [
    {
      "classType": "ECONOMY",
      "basePrice": 850000,
      "totalSeats": 150
    }
  ]
}
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_FLIGHT",
  "message": "Giờ đến phải sau giờ khởi hành"
}
```

---

**Body (điểm đi = điểm đến):**
```json
{
  "flightNumber": "VN998",
  "airlineId": 1,
  "originIata": "SGN",
  "destinationIata": "SGN",
  "departureTime": "2025-07-15T08:00:00",
  "arrivalTime": "2025-07-15T10:00:00",
  "classes": [
    {
      "classType": "ECONOMY",
      "basePrice": 850000,
      "totalSeats": 150
    }
  ]
}
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_FLIGHT",
  "message": "Điểm đi và điểm đến không được trùng nhau"
}
```

---

### 2.3 Cập nhật trạng thái chuyến bay

```
PATCH {{base_url}}/api/v1/admin/flights/1/status
Authorization: Bearer {{admin_token}}
```

**Body (SCHEDULED → DELAYED):**
```json
{
  "status": "DELAYED",
  "delayMinutes": 45,
  "reason": "Thời tiết xấu tại điểm đi"
}
```
**Kết quả mong đợi:** `200 OK`

---

**Body (SCHEDULED → BOARDING):**
```json
{
  "status": "BOARDING"
}
```
**Kết quả mong đợi:** `200 OK`

---

**Body (SCHEDULED → CANCELLED):**
```json
{
  "status": "CANCELLED",
  "reason": "Lỗi kỹ thuật"
}
```
**Kết quả mong đợi:** `200 OK`

---

**Body (ARRIVED → CANCELLED — không hợp lệ):**
```json
{
  "status": "CANCELLED"
}
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_FLIGHT",
  "message": "Không thể chuyển từ ARRIVED sang CANCELLED"
}
```

---

### 2.4 Xem danh sách sân bay

```
GET {{base_url}}/api/v1/airports
```
**Kết quả mong đợi:** `200 OK` + danh sách sân bay

---

### 2.5 Xem chi tiết chuyến bay

```
GET {{base_url}}/api/v1/flights/1
```
**Kết quả mong đợi:** `200 OK` + flight object

---

**Flight không tồn tại:**
```
GET {{base_url}}/api/v1/flights/9999
```
**Kết quả mong đợi:** `404 Not Found`
```json
{
  "code": "NOT_FOUND",
  "message": "Chuyến bay không tồn tại: 9999"
}
```

---

## MODULE 03 — Flight Search

### 3.1 Tìm chuyến bay một chiều

```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2025-07-15&adults=2
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "meta": {
    "from": "SGN",
    "to": "HAN",
    "departDate": "2025-07-15",
    "adults": 2,
    "children": 0,
    "infants": 0,
    "classType": "ECONOMY"
  },
  "flights": [...],
  "priceRange": { "min": 850000, "max": 850000 },
  "availableFilters": { "airlines": ["VN"], ... }
}
```

---

**Tìm kiếm với đầy đủ filter:**
```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2025-07-15&adults=1&children=1&infants=0&classType=ECONOMY&sortBy=PRICE_ASC&minPrice=500000&maxPrice=2000000&airlines=VN&page=0&size=10
```
**Kết quả mong đợi:** `200 OK` + chỉ hiện Vietnam Airlines trong khoảng giá

---

**Ngày quá khứ:**
```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2020-01-01&adults=1
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_SEARCH",
  "message": "Ngày tìm kiếm phải từ ngày mai trở đi"
}
```

---

**Quá 9 hành khách:**
```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2025-07-15&adults=8&children=2
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_SEARCH",
  "message": "Tổng hành khách không được vượt quá 9"
}
```

---

**Gọi lần 2 cùng params (test cache):**
```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2025-07-15&adults=2
```
**Kết quả mong đợi:** `200 OK` — response time nhanh hơn đáng kể (từ Redis)

---

### 3.2 Sắp xếp

```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2025-07-15&adults=1&sortBy=DURATION_ASC
```
**Kết quả mong đợi:** Chuyến bay ngắn nhất ở đầu danh sách

```
GET {{base_url}}/api/v1/flights/search?from=SGN&to=HAN&departDate=2025-07-15&adults=1&sortBy=DEPARTURE_ASC
```
**Kết quả mong đợi:** Chuyến bay khởi hành sớm nhất ở đầu

---

### 3.3 Tìm khứ hồi

```
GET {{base_url}}/api/v1/flights/search/round-trip?from=SGN&to=HAN&departDate=2025-07-15&returnDate=2025-07-20&adults=2
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "outbound": { "flights": [...] },
  "returnTrip": { "flights": [...] },
  "cheapestCombination": {
    "outboundFlight": {...},
    "returnFlight": {...},
    "totalCombinedPrice": 1700000
  }
}
```

---

**Thiếu returnDate:**
```
GET {{base_url}}/api/v1/flights/search/round-trip?from=SGN&to=HAN&departDate=2025-07-15&adults=1
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_SEARCH",
  "message": "Vui lòng cung cấp ngày về cho chuyến khứ hồi"
}
```

---

## MODULE 04 — Booking & Seat Selection

> **Yêu cầu:** Dùng `access_token` của user thường

### 4.1 Xem sơ đồ ghế

```
GET {{base_url}}/api/v1/flights/1/seats
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "economy": [
    {
      "rowNumber": 10,
      "seats": [
        { "seatNumber": "10A", "position": "WINDOW", "isAvailable": true, "extraFee": 0 },
        { "seatNumber": "10B", "position": "MIDDLE", "isAvailable": true, "extraFee": 0 },
        { "seatNumber": "10C", "position": "AISLE",  "isAvailable": true, "extraFee": 0 }
      ]
    }
  ],
  "business": [...],
  "firstClass": [...]
}
```

---

### 4.2 Tạo booking (thành công)

```
POST {{base_url}}/api/v1/bookings
Authorization: Bearer {{access_token}}
```

```json
{
  "flightClassId": 1,
  "contactEmail": "testuser@gmail.com",
  "contactPhone": "0901234567",
  "passengers": [
    {
      "firstName": "Nguyen",
      "lastName": "Van A",
      "dateOfBirth": "1990-05-15",
      "gender": "MALE",
      "nationality": "VN",
      "idType": "CCCD",
      "idNumber": "079090012345",
      "idExpiry": null,
      "passengerType": "ADULT",
      "seatId": 1,
      "extraBaggageKg": 0,
      "mealPreference": "STANDARD"
    },
    {
      "firstName": "Nguyen",
      "lastName": "Thi B",
      "dateOfBirth": "1992-08-20",
      "gender": "FEMALE",
      "nationality": "VN",
      "idType": "CCCD",
      "idNumber": "079092054321",
      "idExpiry": null,
      "passengerType": "ADULT",
      "seatId": 2,
      "extraBaggageKg": 5,
      "mealPreference": "VEGETARIAN"
    }
  ],
  "selectedSeatIds": [1, 2]
}
```
**Kết quả mong đợi:** `201 Created`
```json
{
  "pnrCode": "AB3X9K",
  "status": "PENDING",
  "expiresAt": "2025-07-01T10:45:00",
  "flight": {
    "flightNumber": "VN123",
    "from": "SGN",
    "to": "HAN",
    "departureTime": "2025-07-15T08:00:00"
  },
  "passengers": [
    { "name": "Nguyen Van A", "seat": "10A", "idNumber": "079090012345" },
    { "name": "Nguyen Thi B", "seat": "10B", "idNumber": "079092054321" }
  ],
  "pricing": {
    "subtotal": 1700000,
    "serviceFee": 55000,
    "totalPrice": 1755000,
    "currency": "VND"
  }
}
```
> Lưu `pnrCode` vào biến `pnr_code`

---

### 4.3 Tạo booking với ghế đã bị chọn

Tạo booking thứ 2 với cùng `seatId`:
```json
{
  "flightClassId": 1,
  "contactEmail": "testuser2@gmail.com",
  "contactPhone": "0907654321",
  "passengers": [
    {
      "firstName": "Tran",
      "lastName": "Van C",
      "dateOfBirth": "1995-03-10",
      "nationality": "VN",
      "idType": "CCCD",
      "idNumber": "079095099999",
      "passengerType": "ADULT",
      "seatId": 1
    }
  ],
  "selectedSeatIds": [1]
}
```
**Kết quả mong đợi:** `409 Conflict`
```json
{
  "code": "SEAT_UNAVAILABLE",
  "message": "Ghế 10A đã được chọn"
}
```

---

### 4.4 Tạo booking với CCCD trùng trên cùng chuyến bay

```json
{
  "flightClassId": 1,
  "contactEmail": "another@gmail.com",
  "contactPhone": "0909999999",
  "passengers": [
    {
      "firstName": "Nguyen",
      "lastName": "Van A Duplicate",
      "dateOfBirth": "1990-05-15",
      "nationality": "VN",
      "idType": "CCCD",
      "idNumber": "079090012345",
      "passengerType": "ADULT",
      "seatId": 5
    }
  ],
  "selectedSeatIds": [5]
}
```
**Kết quả mong đợi:** `409 Conflict`
```json
{
  "code": "DUPLICATE_PASSENGER",
  "message": "Một hành khách đã có booking trên chuyến bay này"
}
```

---

### 4.5 Xem chi tiết booking

```
GET {{base_url}}/api/v1/bookings/AB3X9K
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `200 OK` + booking object

---

### 4.6 Hủy booking

```
DELETE {{base_url}}/api/v1/bookings/AB3X9K
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "pnrCode": "AB3X9K",
  "refundAmount": 1228500,
  "cancelledAt": "2025-07-01T10:00:00"
}
```

---

### 4.7 Test booking tự hết hạn

1. Tạo booking mới
2. **Không thanh toán**, đợi 15 phút
3. Kiểm tra DB: `SELECT status FROM bookings WHERE pnr_code = 'XXXXX'`
4. **Kết quả mong đợi:** Status = `EXPIRED`, ghế trả về pool (`is_available = true`)

---

## MODULE 05 — Payment VNPay

### 5.1 Tạo link thanh toán

```
POST {{base_url}}/api/v1/payments/vnpay/create
Authorization: Bearer {{access_token}}
```

```json
{
  "pnrCode": "{{pnr_code}}",
  "returnUrl": "http://localhost:3000/payment/result",
  "ipAddress": "127.0.0.1"
}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=175500000&...",
  "txnRef": "AB3X9K-1719820000000",
  "amount": 1755000,
  "expiresAt": "2025-07-01T10:45:00"
}
```

---

**Booking đã CONFIRMED (tạo link lần 2):**
```json
{
  "pnrCode": "AB3X9K",
  "returnUrl": "http://localhost:3000/payment/result"
}
```
**Kết quả mong đợi:** `400 Bad Request`
```json
{
  "code": "INVALID_PAYMENT",
  "message": "Booking không ở trạng thái chờ thanh toán"
}
```

---

### 5.2 Test thanh toán thật với VNPay Sandbox

1. Lấy `paymentUrl` từ bước 5.1
2. Mở trên browser
3. Chọn **NCB** → nhập thông tin thẻ test:

```
Số thẻ:         9704198526191432198
Tên chủ thẻ:    NGUYEN VAN A
Ngày phát hành: 07/15
OTP:            123456
```

4. Sau khi thanh toán → VNPay redirect về `returnUrl`

---

### 5.3 Kiểm tra trạng thái sau thanh toán

```
GET {{base_url}}/api/v1/payments/status/{{pnr_code}}
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "pnrCode": "AB3X9K",
  "status": "SUCCESS",
  "amount": 1755000,
  "bankCode": "NCB",
  "paidAt": "2025-07-01T10:30:00"
}
```

---

### 5.4 Kiểm tra booking đã CONFIRMED

```
GET {{base_url}}/api/v1/bookings/{{pnr_code}}
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `status: "CONFIRMED"`

---

### 5.5 Test IPN thủ công (không cần VNPay)

```
GET {{base_url}}/api/v1/payments/vnpay/ipn?vnp_Amount=175500000&vnp_BankCode=NCB&vnp_ResponseCode=00&vnp_TxnRef=AB3X9K-1719820000000&vnp_TransactionNo=14057194&vnp_SecureHash=<hash>
```
> **Lưu ý:** `vnp_SecureHash` phải tính đúng HMAC-SHA512 mới pass được verify.  
> Dùng [VNPay Hash Generator](https://sandbox.vnpayment.vn) hoặc viết script tính hash.

---

## MODULE 06 — Email Notification

> Email được gửi **async** — kiểm tra sau vài giây

### 6.1 Test email xác nhận booking

- Sau khi thanh toán thành công (5.2), kiểm tra email tại địa chỉ `contactEmail`
- **Kết quả mong đợi:** Nhận email "✈️ Xác nhận đặt vé" với PNR, thông tin chuyến bay, danh sách hành khách

### 6.2 Test MailHog (local, không cần Gmail thật)

```bash
# Khởi động MailHog
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Cập nhật `application.yml`:
```yaml
spring:
  mail:
    host: localhost
    port: 1025
    username: test
    password: test
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false
```

Mở `http://localhost:8025` → xem email nhận được.

### 6.3 Test retry email

1. Dừng SMTP server (stop MailHog)
2. Trigger gửi email (thanh toán thành công)
3. Kiểm tra DB: `SELECT * FROM email_logs WHERE status = 'PENDING'`
4. Khởi động lại MailHog
5. Đợi tối đa 1 phút (Scheduler chạy)
6. **Kết quả mong đợi:** Email được gửi, `status = 'SENT'`, `attempts = 2`

### 6.4 Test email hủy booking

- Hủy booking đã CONFIRMED
- **Kết quả mong đợi:** Nhận email "❌ Thông báo hủy vé" với `refundAmount`

---

## MODULE 07 — Admin Dashboard

> **Yêu cầu:** Dùng `admin_token`

### 7.1 Xem KPI Dashboard

```
GET {{base_url}}/api/v1/admin/dashboard/kpis
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** `200 OK`
```json
{
  "date": "2025-07-01",
  "todayRevenue": 1755000,
  "yesterdayRevenue": 0,
  "revenueGrowthPercent": 0,
  "todayBookings": 1,
  "confirmedBookings": 1,
  "pendingBookings": 0,
  "cancelledBookings": 0,
  "conversionRate": 100.0,
  "totalFlights": 1,
  "delayedFlights": 0,
  "cancelledFlights": 0,
  "avgTicketPrice": 1755000,
  "updatedAt": "2025-07-01T10:35:00"
}
```

---

**Gọi lần 2 — test cache:**
```
GET {{base_url}}/api/v1/admin/dashboard/kpis
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** Response nhanh hơn (từ Redis, TTL 5 phút)

---

### 7.2 Biểu đồ doanh thu

```
GET {{base_url}}/api/v1/admin/dashboard/revenue-chart?period=MONTHLY
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** `200 OK` + dữ liệu 30 ngày

```
GET {{base_url}}/api/v1/admin/dashboard/revenue-chart?period=WEEKLY
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** `200 OK` + dữ liệu 7 ngày

---

### 7.3 Top chặng bay

```
GET {{base_url}}/api/v1/admin/dashboard/top-routes?limit=5
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** `200 OK`
```json
[
  {
    "origin": "SGN",
    "destination": "HAN",
    "airline": "Vietnam Airlines",
    "totalBookings": 1,
    "totalRevenue": 1755000
  }
]
```

---

### 7.4 Export báo cáo Excel

```
POST {{base_url}}/api/v1/admin/reports/export
Authorization: Bearer {{admin_token}}
```

```json
{
  "fromDate": "2025-07-01",
  "toDate": "2025-07-31",
  "type": "REVENUE"
}
```
**Kết quả mong đợi:** File `.xlsx` download tự động

> Trong Postman: **Save to file** → mở bằng Excel kiểm tra format

---

### 7.5 Quản lý booking (Admin)

```
GET {{base_url}}/api/v1/admin/bookings?status=CONFIRMED&page=0&size=20
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** `200 OK` + danh sách booking phân trang

---

```
PATCH {{base_url}}/api/v1/admin/bookings/AB3X9K/cancel?reason=Vi%20pham%20dieu%20khoan
Authorization: Bearer {{admin_token}}
```
**Kết quả mong đợi:** `200 OK`

---

### 7.6 User thường gọi Admin API

```
GET {{base_url}}/api/v1/admin/dashboard/kpis
Authorization: Bearer {{access_token}}
```
**Kết quả mong đợi:** `403 Forbidden`
```json
{
  "message": "Forbidden"
}
```

---

## MODULE 08 — Token Blacklist

### 8.1 Dùng token sau khi logout

```
# Bước 1: Login lấy token
POST {{base_url}}/api/auth/login
Body: { "email": "testuser@gmail.com", "password": "Test@123456" }

# Bước 2: Gọi API — thành công
GET {{base_url}}/api/v1/flights/1
Authorization: Bearer {{access_token}}
→ 200 OK

# Bước 3: Logout
POST {{base_url}}/api/auth/logout
Authorization: Bearer {{access_token}}
Body: { "refreshToken": "{{refresh_token}}" }
→ 200 OK

# Bước 4: Gọi lại API với token cũ
GET {{base_url}}/api/v1/flights/1
Authorization: Bearer {{access_token}}
→ 401 Unauthorized
```
**Kết quả mong đợi bước 4:**
```json
{
  "message": "Token đã bị thu hồi, vui lòng đăng nhập lại"
}
```

---

### 8.2 Reuse Detection

```
# Bước 1: Login lấy token
POST {{base_url}}/api/auth/login
→ accessToken_A, refreshToken_A

# Bước 2: Refresh lần 1 — hợp lệ
POST {{base_url}}/api/auth/refresh
Body: { "refreshToken": "refreshToken_A" }
→ accessToken_B, refreshToken_B

# Bước 3: Dùng refreshToken_A lần 2 — REUSE DETECTION
POST {{base_url}}/api/auth/refresh
Body: { "refreshToken": "refreshToken_A" }
→ 401 Unauthorized
```
**Kết quả mong đợi bước 3:**
```json
{
  "message": "Phát hiện token bị tái sử dụng. Tất cả phiên đã bị đăng xuất."
}
```

---

### 8.3 Dùng accessToken_B sau Reuse Detection

```
GET {{base_url}}/api/v1/flights/1
Authorization: Bearer accessToken_B
→ 401 Unauthorized
```
**Kết quả mong đợi:**
```json
{
  "message": "Token đã bị thu hồi, vui lòng đăng nhập lại"
}
```

---

### 8.4 Kiểm tra Redis trực tiếp

```bash
# Kết nối Redis CLI
docker exec -it <redis_container> redis-cli

# Kiểm tra blacklist key tồn tại
KEYS blacklist:token:*

# Xem TTL còn lại (giây)
TTL blacklist:token:<access_token>

# Kết quả mong đợi: số giây còn lại < 900 (15 phút)
```

---

## Thứ tự chạy test toàn bộ

```
01. Register user + admin
02. Login user    → lưu access_token
03. Login admin   → lưu admin_token
04. Tạo airport   (admin)
05. Tạo flight    (admin)
06. Search flight (public)
07. Xem seat map  (user)
08. Tạo booking   (user) → lưu pnr_code
09. Tạo link VNPay (user)
10. Thanh toán    (browser)
11. Kiểm tra payment status
12. Kiểm tra email xác nhận
13. Xem KPI dashboard (admin)
14. Export Excel  (admin)
15. Logout        (user)
16. Dùng token cũ → 401
```
