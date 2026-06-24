# Hướng dẫn Test Frontend — FlightEasy

> **FE:** `http://localhost:3000`  
> **BE:** `http://localhost:8080`  
> **Yêu cầu:** BE đang chạy, DB đã có dữ liệu mẫu

---

## Chuẩn bị trước khi test

### 1. Khởi động BE

```bash
cd flighteasy-backend
./mvnw spring-boot:run
```

### 2. Khởi động FE

```bash
cd flighteasy-frontend
npm run dev
# Truy cập: http://localhost:3000
```

### 3. Dữ liệu mẫu (chạy SQL nếu chưa có)

```sql
-- Roles
INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN')
ON CONFLICT DO NOTHING;

-- Airlines
INSERT INTO airlines (iata_code, name, is_active)
VALUES ('VN', 'Vietnam Airlines', true), ('VJ', 'VietJet Air', true)
ON CONFLICT DO NOTHING;

-- Airports
INSERT INTO airports (iata_code, name, city, country, country_code, timezone, is_active)
VALUES
  ('SGN', 'Tân Sơn Nhất', 'Hồ Chí Minh', 'Việt Nam', 'VN', 'Asia/Ho_Chi_Minh', true),
  ('HAN', 'Nội Bài', 'Hà Nội', 'Việt Nam', 'VN', 'Asia/Ho_Chi_Minh', true),
  ('DAD', 'Đà Nẵng', 'Đà Nẵng', 'Việt Nam', 'VN', 'Asia/Ho_Chi_Minh', true)
ON CONFLICT DO NOTHING;

-- Aircraft type
INSERT INTO aircraft_types (code, name, total_seats, economy_seats, business_seats, first_class_seats)
VALUES ('A321', 'Airbus A321', 220, 196, 24, 0)
ON CONFLICT DO NOTHING;
```

> Tạo chuyến bay test qua Admin UI (xem **Luồng 5**) hoặc dùng Postman tạo trước.

---

## Luồng 1 — Đăng ký tài khoản

**URL:** `http://localhost:3000/register`

| Bước | Thao tác | Kết quả mong đợi |
|------|----------|-----------------|
| 1 | Nhập Họ tên: `Nguyễn Văn Test` | — |
| 2 | Nhập Email: `testuser@gmail.com` | — |
| 3 | Nhập Mật khẩu: `Test@123456` | — |
| 4 | Nhập Xác nhận mật khẩu | — |
| 5 | Click **Đăng ký** | Toast "Đăng ký thành công!" → redirect `/login` |

**Test lỗi:**

| Trường hợp | Hành động | Kết quả mong đợi |
|-----------|-----------|-----------------|
| Email đã tồn tại | Đăng ký lại cùng email | Toast lỗi "Email đã tồn tại" |
| Bỏ trống trường | Submit form rỗng | Hiện validation error dưới từng input |
| Mật khẩu không khớp | Nhập confirm khác | Lỗi "Mật khẩu không khớp" |

---

## Luồng 2 — Đăng nhập

**URL:** `http://localhost:3000/login`

| Bước | Thao tác | Kết quả mong đợi |
|------|----------|-----------------|
| 1 | Nhập email + mật khẩu đúng | — |
| 2 | Click **Đăng nhập** | Toast "Đăng nhập thành công!" → redirect `/` |
| 3 | Kiểm tra Navbar | Hiện tên user + nút Logout |

**Test lỗi:**

| Trường hợp | Kết quả mong đợi |
|-----------|-----------------|
| Sai mật khẩu | Toast "Email hoặc mật khẩu không đúng" |
| Email không tồn tại | Toast lỗi từ BE |
| Sai 5 lần liên tiếp | Toast "Tài khoản bị khóa đến ..." |

**Kiểm tra token trong localStorage (F12 → Application → Local Storage):**
```
access_token = eyJhbGci...
```

---

## Luồng 3 — Tìm kiếm chuyến bay

**URL:** `http://localhost:3000/` (SearchPage)

| Bước | Thao tác | Kết quả mong đợi |
|------|----------|-----------------|
| 1 | Chọn **Điểm đi:** `SGN` hoặc `Hồ Chí Minh` | Dropdown gợi ý sân bay |
| 2 | Chọn **Điểm đến:** `HAN` hoặc `Hà Nội` | — |
| 3 | Chọn **Ngày đi** (ngày có chuyến bay) | Date picker |
| 4 | Chọn số hành khách (mặc định 1 người lớn) | — |
| 5 | Click **Tìm chuyến bay** | Redirect `/search/results` |

**Trang kết quả** `http://localhost:3000/search/results`:

| Kiểm tra | Kết quả mong đợi |
|---------|-----------------|
| Danh sách chuyến bay hiển thị | FlightCard với giá, giờ bay, hãng |
| Filter theo giá | Danh sách lọc đúng |
| Sort theo giờ khởi hành / giá | Thứ tự thay đổi |
| Click vào chuyến bay | Chọn hạng ghế (Economy/Business) |

**Test không có kết quả:**
- Tìm ngày không có chuyến → Hiện "Không tìm thấy chuyến bay phù hợp"

---

## Luồng 4 — Đặt vé & Chọn ghế

> **Yêu cầu:** Đã đăng nhập

**Từ SearchResultsPage:**

| Bước | Thao tác | Kết quả mong đợi |
|------|----------|-----------------|
| 1 | Click chuyến bay → chọn hạng Economy | — |
| 2 | Redirect `/booking` | BookingPage hiển thị |
| 3 | Xem sơ đồ ghế (SeatMap) | Ghế xanh = còn trống, đỏ = đã đặt |
| 4 | Click chọn ghế (ví dụ: 10A) | Ghế được highlight |
| 5 | Điền thông tin hành khách: họ tên, ngày sinh, CCCD | — |
| 6 | Điền email liên hệ, SĐT | — |
| 7 | Click **Xác nhận đặt vé** | Loading → redirect `/booking/confirm/:pnr` |

**Trang xác nhận** `/booking/confirm/AB3X9K`:

| Kiểm tra | Kết quả mong đợi |
|---------|-----------------|
| Hiện PNR code | Ví dụ: `AB3X9K` |
| Thông tin chuyến bay | Số hiệu, giờ bay, ghế |
| Thông tin hành khách | Đúng với vừa nhập |
| Tổng tiền | Giá vé + phụ phí ghế + hành lý |
| Nút **Thanh toán VNPay** | Hiển thị rõ |

**Test chưa đăng nhập:**
- Truy cập `/booking` trực tiếp → redirect `/login`

**Test ghế đã bị đặt:**
- Ghế màu đỏ không click được

---

## Luồng 5 — Thanh toán VNPay

> **Yêu cầu:** ngrok đang chạy để nhận IPN callback

```bash
# Terminal riêng
ngrok http 8080
# Copy URL ngrok → cấu hình VNPay IPN URL trong application.yml
```

| Bước | Thao tác | Kết quả mong đợi |
|------|----------|-----------------|
| 1 | Từ BookingConfirmPage, click **Thanh toán VNPay** | Loading → redirect sang VNPay |
| 2 | Trang VNPay: chọn **Thẻ nội địa (ATM)** | — |
| 3 | Nhập thông tin thẻ test VNPay | Xem bảng thẻ test bên dưới |
| 4 | Xác nhận OTP (nếu có) | — |
| 5 | VNPay redirect về `/payment/result` | — |

**Thẻ test VNPay:**

| Thông tin | Giá trị |
|-----------|---------|
| Ngân hàng | NCB |
| Số thẻ | `9704198526191432198` |
| Tên | `NGUYEN VAN A` |
| Ngày phát hành | `07/15` |
| OTP | `123456` |

**Trang kết quả** `/payment/result`:

| Trường hợp | Kết quả mong đợi |
|-----------|-----------------|
| Thanh toán thành công | Icon ✅, "Thanh toán thành công!", hiện PNR |
| Thanh toán thất bại | Icon ❌, "Thanh toán thất bại", nút thử lại |
| Kiểm tra email | Nhận email "Xác nhận đặt vé" trong hộp thư |

---

## Luồng 6 — Xem lịch sử đặt vé (My Bookings)

**URL:** `http://localhost:3000/bookings` *(nếu có route)*

| Kiểm tra | Kết quả mong đợi |
|---------|-----------------|
| Danh sách booking của user hiện tại | Hiện đúng booking vừa đặt |
| Badge trạng thái | `CONFIRMED` (màu xanh), `PENDING` (vàng), `CANCELLED` (đỏ) |
| Click xem chi tiết | Hiện đầy đủ thông tin |
| Nút hủy booking | Hiện nếu trạng thái cho phép hủy |

---

## Luồng 7 — Admin Dashboard

**Yêu cầu:** Đăng nhập bằng tài khoản Admin

```
Email: admin@flighteasy.vn
Password: Admin@123456
```

**URL:** `http://localhost:3000/admin`

> Nếu login bằng tài khoản Admin, Navbar sẽ có link **Admin** hoặc tự redirect `/admin`.

| Kiểm tra | Kết quả mong đợi |
|---------|-----------------|
| KPI cards | Doanh thu hôm nay, số booking, tỉ lệ chuyển đổi |
| Biểu đồ doanh thu | Line chart 7 ngày / 30 ngày |
| Top chặng bay | Danh sách top routes |
| Bảng booking | Danh sách tất cả booking, phân trang |
| Nút Export Excel | Download file `.xlsx` |
| Hủy booking (Admin) | Nhập lý do → booking chuyển CANCELLED |

**Test phân quyền:**
- Đăng nhập bằng tài khoản User thường → truy cập `/admin` → redirect về `/`

---

## Luồng 8 — Logout & Token Blacklist

| Bước | Thao tác | Kết quả mong đợi |
|------|----------|-----------------|
| 1 | Click **Logout** trên Navbar | Toast "Đăng xuất thành công" |
| 2 | Redirect về `/login` | — |
| 3 | localStorage `access_token` bị xóa | Kiểm tra F12 → Application |
| 4 | Truy cập `/booking` thủ công | Redirect `/login` |

**Kiểm tra Token Blacklist (Redis):**
```bash
docker exec -it <redis_container> redis-cli
KEYS blacklist:token:*
# → Có key tương ứng với token vừa logout
```

---

## Checklist lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách fix |
|-------------|-------------|----------|
| Trang trắng, console lỗi CORS | Vite proxy chưa hoạt động | Kiểm tra `vite.config.ts` có proxy `/api` → `localhost:8080` |
| API call 401 ngay khi mới vào | Token hết hạn, interceptor không refresh | Kiểm tra `axios.ts` interceptor 401 |
| Redirect vòng lặp `/login` | `isAuthenticated` trong Zustand bị reset | Kiểm tra `persist` trong `authStore.ts` |
| VNPay redirect về nhưng status sai | IPN chưa nhận được (ngrok chưa bật) | Bật ngrok, cập nhật IPN URL |
| Sơ đồ ghế không load | `flightClassId` sai, API trả 404 | Kiểm tra Network tab, đúng endpoint `/flights/{id}/seats` |
| Admin page trắng | Role không khớp (`ROLE_ADMIN` vs `ADMIN`) | Kiểm tra `user.role` trong authStore và `AdminRoute` |

---

## Kiểm tra nhanh Network Tab (F12)

Với mọi API call, mở **F12 → Network → XHR/Fetch**:

| Cần kiểm tra | Chỗ xem |
|-------------|---------|
| Request URL | Phải là `/api/v1/...` (không phải `localhost:8080`) |
| Authorization header | `Bearer eyJhbGci...` |
| Response status | 200/201 = OK, 4xx = lỗi logic, 5xx = lỗi BE |
| Response body | Xem message lỗi cụ thể |
