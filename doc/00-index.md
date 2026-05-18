# ✈️ FlightEasy — Tài liệu Thiết kế Hệ thống

> **Project:** FlightEasy — Ứng dụng đặt vé máy bay  
> **Stack:** Spring Boot 3.2 · PostgreSQL · Redis · VNPay  
> **Mục tiêu:** Portfolio xin việc — Backend Java

---

## Danh sách tài liệu

| # | Service | File | Trạng thái |
|---|---------|------|------------|
| 01 | Authentication & Refresh Token | `01-auth-refresh-token.md` | ✅ Waiting  |
| 02 | Quản lý chuyến bay (Flight CRUD) | `02-flight-management.md` |            |
| 03 | Tìm kiếm & Lọc chuyến bay | `03-flight-search.md` |            |
| 04 | Đặt vé & Chọn ghế | `04-booking-seat.md` |            |
| 05 | Thanh toán VNPay | `05-payment-vnpay.md` |            |
| 06 | Email Notification | `06-email-notification.md` |            |
| 07 | Admin Dashboard & Báo cáo | `07-admin-dashboard.md` |            |

---

## Kiến trúc tổng quan

```
┌──────────────────────────────────────────────────────────────┐
│                    CLIENT (FE / Postman)                      │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTPS
            ┌───────────────▼───────────────┐
            │      Spring Boot Application   │
            │                               │
            │  ┌─────────┐  ┌───────────┐  │
            │  │Security │  │ Controllers│  │
            │  │ Filter  │─►│ (REST API)│  │
            │  └─────────┘  └─────┬─────┘  │
            │                     │        │
            │              ┌──────▼──────┐ │
            │              │  Services   │ │
            │              │ (Business)  │ │
            │              └──────┬──────┘ │
            │                     │        │
            │        ┌────────────┼──────┐ │
            │        │            │      │ │
            │   ┌────▼───┐ ┌─────▼──┐   │ │
            │   │  Redis  │ │  JPA   │   │ │
            │   │ (Cache) │ │ Repos  │   │ │
            │   └────────┘ └────┬───┘   │ │
            └────────────────────┼───────┘
                                 │
                         ┌───────▼────────┐
                         │  PostgreSQL 16  │
                         └───────────────┘
                                 │
                    ┌────────────┴──────────┐
                    │   External Services    │
                    │  VNPay · SMTP · OAuth  │
                    └───────────────────────┘
```

---

## Flow tổng quát — Happy Path

```
1. User đăng ký / đăng nhập
       ↓
2. Tìm kiếm chuyến bay (SGN → HAN, 15/06)
       ↓
3. Chọn chuyến + hạng vé
       ↓
4. Nhập thông tin hành khách + chọn ghế
       ↓
5. Tạo Booking (PENDING, giữ ghế 15 phút)
       ↓
6. Thanh toán VNPay
       ↓
7. VNPay IPN callback → Booking CONFIRMED
       ↓
8. Email xác nhận gửi cho user
       ↓
9. Sáng hôm trước: Email nhắc check-in
```

---

## Tech Stack

| Layer | Công nghệ | Lý do |
|-------|-----------|-------|
| Framework | Spring Boot 3.2 | Tiêu chuẩn ngành Java |
| Security | Spring Security 6 + JWT | Stateless, phù hợp REST API |
| Database | PostgreSQL 16 | ACID, JSONB, Window Functions |
| Cache | Redis 7 | TTL sẵn, tốc độ cao |
| ORM | Spring Data JPA + Hibernate | Giảm boilerplate |
| Migration | Flyway | Version control cho DB schema |
| Mapping | MapStruct | Compile-time, nhanh hơn ModelMapper |
| Template | Thymeleaf | Email HTML template |
| Payment | VNPay SDK | Cổng thanh toán nội địa phổ biến nhất |
| Docs | Swagger / OpenAPI 3 | Auto-generate API docs |
| Build | Maven | Standard, ổn định |
| Container | Docker + Docker Compose | Reproduce môi trường dễ dàng |
| CI/CD | GitHub Actions | Miễn phí, tích hợp sẵn |

---

## Lộ trình phát triển

| Phase | Nội dung | Tuần |
|-------|----------|------|
| 1 | Auth: Register, Login, JWT, Refresh Token | 1 |
| 2 | Airport, Airline, Flight CRUD | 2 |
| 3 | Flight Search + Redis Cache | 3 |
| 4 | Booking + Seat Selection + Scheduler | 4-5 |
| 5 | VNPay Payment Integration | 6 |
| 6 | Email Notification (Async + Template) | 7 |
| 7 | Admin Dashboard + Reports + Export Excel | 8 |
| 8 | Unit Test + Integration Test + Docker | 9 |
| 9 | Deploy lên Railway/Render + README | 10 |
