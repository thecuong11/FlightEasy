# Service 07 — Admin Dashboard & Báo cáo (Admin Dashboard)

> **Module:** Admin Service  
> **Phiên bản:** 1.0  
> **Độ ưu tiên:** P1 — Quản trị vận hành và ra quyết định kinh doanh

---

## 1. Nghiệp vụ

### 1.1 Mô tả

Admin dashboard cung cấp tổng quan vận hành theo thời gian thực và báo cáo doanh thu theo nhiều chiều: ngày, tháng, năm, chặng bay, hãng bay. Admin có thể quản lý toàn bộ dữ liệu và export báo cáo.

### 1.2 Các nhóm chức năng Admin

| Nhóm | Chức năng |
|------|-----------|
| **Dashboard** | KPI tổng quan, biểu đồ real-time |
| **Booking Management** | Xem, lọc, hủy, hoàn tiền booking |
| **Flight Management** | CRUD chuyến bay, cập nhật trạng thái hàng loạt |
| **User Management** | Xem, khóa/mở tài khoản người dùng |
| **Revenue Reports** | Doanh thu theo thời gian, chặng bay, hãng bay |
| **Export** | Xuất báo cáo Excel/CSV |

### 1.3 KPI trên Dashboard

| KPI | Mô tả | Cập nhật |
|-----|-------|---------|
| `totalRevenue` | Tổng doanh thu hôm nay | Mỗi 5 phút |
| `totalBookings` | Tổng số booking hôm nay | Mỗi 5 phút |
| `confirmedBookings` | Booking đã thanh toán | Mỗi 5 phút |
| `cancelledBookings` | Booking bị hủy | Mỗi 5 phút |
| `conversionRate` | Tỷ lệ booking → confirmed | Mỗi 5 phút |
| `avgTicketPrice` | Giá vé trung bình hôm nay | Mỗi 5 phút |
| `flightsToday` | Số chuyến bay hôm nay | Tĩnh |
| `delayedFlights` | Số chuyến bay bị delay | Realtime |

### 1.4 Quy tắc nghiệp vụ

| STT | Quy tắc |
|-----|---------|
| BR-01 | Chỉ user có role ROLE_ADMIN mới truy cập được |
| BR-02 | Dashboard data được cache Redis 5 phút |
| BR-03 | Export báo cáo chạy bất đồng bộ nếu dữ liệu > 10,000 bản ghi |
| BR-04 | Log tất cả hành động của Admin (audit log) |
| BR-05 | Admin không thể xóa cứng (hard delete) booking — chỉ soft cancel |
| BR-06 | Báo cáo doanh thu chỉ tính booking có status = CONFIRMED hoặc COMPLETED |

---

## 2. Flow Diagram

### 2.1 Flow Dashboard KPI

```
Admin FE          AdminController        DashboardService          Redis          DB
   │                    │                      │                     │              │
   │ GET /admin/         │                      │                     │              │
   │ dashboard/kpis      │                      │                     │              │
   │───────────────────►│                      │                     │              │
   │                    │ getDashboardKPIs()    │                     │              │
   │                    │─────────────────────►│                     │              │
   │                    │                      │ get("dashboard-kpis")              │
   │                    │                      │────────────────────►│              │
   │                    │                      │                     │              │
   │                    │                      │ [Cache HIT, < 5min] │              │
   │◄───────────────────│                      │◄────────────────────│              │
   │ {kpis from cache}  │                      │                     │              │
   │                    │                      │ [Cache MISS]        │              │
   │                    │                      │ queryKPIs() ────────────────────► │
   │                    │                      │◄──────────────────────────────────│
   │                    │                      │ set("dashboard-kpis", TTL 5min)   │
   │                    │                      │────────────────────►│              │
   │◄───────────────────│                      │                     │              │
   │ {kpis from DB}     │                      │                     │              │
```

### 2.2 Flow Export Báo cáo Excel

```
Admin           AdminController       ReportService         DB         Storage
  │                   │                    │                 │              │
  │ POST /admin/       │                    │                 │              │
  │ reports/export     │                    │                 │              │
  │ {type, dateRange}  │                    │                 │              │
  │──────────────────►│                    │                 │              │
  │                   │ exportReport()      │                 │              │
  │                   │───────────────────►│                 │              │
  │                   │                    │ countRecords()  │              │
  │                   │                    │────────────────►│              │
  │                   │                    │                 │              │
  │                   │                    │ [≤ 10,000 records]             │
  │                   │                    │ queryData()     │              │
  │                   │                    │────────────────►│              │
  │                   │                    │ buildExcel()    │              │
  │◄──────────────────│                    │                 │              │
  │ File download      │                    │                 │              │
  │                   │                    │ [> 10,000 records — Async]     │
  │◄──────────────────│                    │                 │              │
  │ 202 Accepted       │                    │                 │              │
  │ {jobId: "job_123"} │                    │                 │              │
  │                   │   [@Async job]      │                 │              │
  │                   │                    │ queryData()─────►│              │
  │                   │                    │ buildExcel()    │              │
  │                   │                    │ uploadFile()────────────────── ►│
  │                   │                    │ notifyAdmin() [email]           │
  │                   │                    │                 │              │
  │ GET /admin/        │                    │                 │              │
  │ reports/download   │                    │                 │              │
  │ /job_123           │                    │                 │              │
  │──────────────────►│                    │                 │              │
  │◄──────────────────│                    │                 │              │
  │ File download      │                    │                 │              │
```

---

## 3. Database Schema (Reporting Views)

```sql
-- View tổng hợp doanh thu theo ngày
CREATE MATERIALIZED VIEW daily_revenue AS
SELECT
    DATE(b.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS revenue_date,
    COUNT(b.id)                                           AS total_bookings,
    SUM(b.total_price)                                    AS total_revenue,
    AVG(b.total_price)                                    AS avg_ticket_price,
    COUNT(CASE WHEN b.status = 'CANCELLED' THEN 1 END)   AS cancelled_count,
    SUM(b.refund_amount)                                  AS total_refunded
FROM bookings b
WHERE b.status IN ('CONFIRMED', 'COMPLETED', 'CANCELLED')
GROUP BY DATE(b.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
WITH DATA;

-- Refresh hàng ngày lúc 1:00 AM
-- REFRESH MATERIALIZED VIEW daily_revenue;

-- View doanh thu theo chặng bay
CREATE VIEW revenue_by_route AS
SELECT
    ao.iata_code           AS origin,
    ad.iata_code           AS destination,
    al.name                AS airline,
    COUNT(b.id)            AS total_bookings,
    SUM(b.total_price)     AS total_revenue,
    AVG(b.total_price)     AS avg_price
FROM bookings b
JOIN booking_segments bs ON bs.booking_id = b.id
JOIN flight_classes fc ON fc.id = bs.flight_class_id
JOIN flights f ON f.id = fc.flight_id
JOIN airports ao ON ao.id = f.origin_id
JOIN airports ad ON ad.id = f.destination_id
JOIN airlines al ON al.id = f.airline_id
WHERE b.status IN ('CONFIRMED', 'COMPLETED')
GROUP BY ao.iata_code, ad.iata_code, al.name;

-- Audit log
CREATE TABLE admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,   -- CANCEL_BOOKING, UPDATE_FLIGHT_STATUS...
    entity_type VARCHAR(50),             -- BOOKING, FLIGHT, USER
    entity_id   VARCHAR(50),
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_admin ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX idx_audit_entity ON admin_audit_logs(entity_type, entity_id);
```

---

## 4. API Specification

### Dashboard

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/v1/admin/dashboard/kpis` | KPI tổng quan hôm nay |
| GET | `/api/v1/admin/dashboard/revenue-chart?period=MONTHLY` | Dữ liệu biểu đồ doanh thu |
| GET | `/api/v1/admin/dashboard/top-routes?limit=10` | Top 10 chặng bay hot nhất |
| GET | `/api/v1/admin/dashboard/recent-bookings?limit=10` | 10 booking gần nhất |

### Booking Management

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/v1/admin/bookings` | Danh sách tất cả booking (filter, pagination) |
| GET | `/api/v1/admin/bookings/{pnr}` | Chi tiết booking |
| PATCH | `/api/v1/admin/bookings/{pnr}/cancel` | Hủy booking |
| POST | `/api/v1/admin/payments/{id}/refund` | Hoàn tiền |

### Reports & Export

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/v1/admin/reports/revenue` | Báo cáo doanh thu (JSON) |
| POST | `/api/v1/admin/reports/export` | Export Excel/CSV (async) |
| GET | `/api/v1/admin/reports/download/{jobId}` | Download file đã export |

### Response mẫu — Dashboard KPIs

```json
{
  "date": "2025-06-10",
  "revenue": {
    "today": 125000000,
    "yesterday": 98000000,
    "growthPercent": 27.5
  },
  "bookings": {
    "today": 87,
    "confirmed": 72,
    "pending": 10,
    "cancelled": 5,
    "conversionRate": 82.8
  },
  "flights": {
    "total": 24,
    "scheduled": 20,
    "delayed": 3,
    "cancelled": 1
  },
  "avgTicketPrice": 1724000,
  "updatedAt": "2025-06-10T10:45:00"
}
```

### Response mẫu — Revenue Chart (Monthly)

```json
{
  "period": "MONTHLY",
  "year": 2025,
  "data": [
    { "month": 1, "revenue": 980000000, "bookings": 620 },
    { "month": 2, "revenue": 750000000, "bookings": 480 },
    { "month": 3, "revenue": 1120000000, "bookings": 710 },
    { "month": 4, "revenue": 1350000000, "bookings": 860 },
    { "month": 5, "revenue": 1580000000, "bookings": 1020 },
    { "month": 6, "revenue": 890000000, "bookings": 560 }
  ],
  "total": { "revenue": 6670000000, "bookings": 4250 }
}
```

---

## 5. Code mẫu

### DashboardService — Query KPIs với cache

```java
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KPI_CACHE_KEY = "admin:dashboard:kpis";
    private static final Duration KPI_TTL = Duration.ofMinutes(5);

    @SuppressWarnings("unchecked")
    public DashboardKPIResponse getKPIs() {
        // Thử cache trước
        Object cached = redisTemplate.opsForValue().get(KPI_CACHE_KEY);
        if (cached != null) {
            return (DashboardKPIResponse) cached;
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23, 59, 59);

        // Query tất cả KPI trong 1 lần (tránh N+1)
        BookingStats todayStats = bookingRepository.getStatsByDateRange(start, end);
        BookingStats yesterdayStats = bookingRepository.getStatsByDateRange(start.minusDays(1), end.minusDays(1));
        FlightStats flightStats = flightRepository.getTodayStats(today);

        double growthPercent = yesterdayStats.getTotalRevenue().compareTo(BigDecimal.ZERO) == 0
            ? 0
            : todayStats.getTotalRevenue()
                .subtract(yesterdayStats.getTotalRevenue())
                .divide(yesterdayStats.getTotalRevenue(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        double conversionRate = todayStats.getTotalBookings() == 0
            ? 0
            : (double) todayStats.getConfirmedBookings() / todayStats.getTotalBookings() * 100;

        DashboardKPIResponse response = DashboardKPIResponse.builder()
                .date(today)
                .todayRevenue(todayStats.getTotalRevenue())
                .yesterdayRevenue(yesterdayStats.getTotalRevenue())
                .revenueGrowthPercent(growthPercent)
                .todayBookings(todayStats.getTotalBookings())
                .confirmedBookings(todayStats.getConfirmedBookings())
                .pendingBookings(todayStats.getPendingBookings())
                .cancelledBookings(todayStats.getCancelledBookings())
                .conversionRate(conversionRate)
                .totalFlights(flightStats.getTotal())
                .delayedFlights(flightStats.getDelayed())
                .cancelledFlights(flightStats.getCancelled())
                .avgTicketPrice(todayStats.getAvgPrice())
                .updatedAt(LocalDateTime.now())
                .build();

        redisTemplate.opsForValue().set(KPI_CACHE_KEY, response, KPI_TTL);
        return response;
    }
}
```

### ReportService — Export Excel với Apache POI

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final BookingRepository bookingRepository;

    @Async("reportTaskExecutor")
    public void exportRevenueReportAsync(ReportRequest request, String jobId) {
        try {
            List<BookingReportRow> data = bookingRepository.findForReport(
                request.getFromDate(), request.getToDate()
            );

            byte[] excelBytes = buildExcelFile(data, request);
            // Lưu vào temp storage hoặc S3
            storageService.save(jobId + ".xlsx", excelBytes);
            reportJobService.markComplete(jobId);

        } catch (Exception e) {
            reportJobService.markFailed(jobId, e.getMessage());
        }
    }

    private byte[] buildExcelFile(List<BookingReportRow> data, ReportRequest request)
            throws IOException {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Báo cáo doanh thu");

            // Style header
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.index);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.index);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Header row
            String[] headers = {
                "STT", "Mã PNR", "Ngày đặt", "Chặng bay",
                "Hành khách", "Hạng vé", "Giá vé", "Phí DV",
                "Tổng tiền", "Trạng thái", "Hãng bay"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Style tiền tệ
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("#,##0 [$₫-42A]"));

            // Data rows
            for (int i = 0; i < data.size(); i++) {
                BookingReportRow row = data.get(i);
                Row excelRow = sheet.createRow(i + 1);

                // Xen kẽ màu nền cho dễ đọc
                if (i % 2 == 0) {
                    CellStyle altStyle = workbook.createCellStyle();
                    altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.index);
                    altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }

                excelRow.createCell(0).setCellValue(i + 1);
                excelRow.createCell(1).setCellValue(row.getPnrCode());
                excelRow.createCell(2).setCellValue(row.getBookingDate().toString());
                excelRow.createCell(3).setCellValue(row.getRoute()); // SGN → HAN
                excelRow.createCell(4).setCellValue(row.getPassengerCount());
                excelRow.createCell(5).setCellValue(row.getClassType());

                Cell priceCell = excelRow.createCell(6);
                priceCell.setCellValue(row.getSubtotal().doubleValue());
                priceCell.setCellStyle(currencyStyle);

                Cell feeCell = excelRow.createCell(7);
                feeCell.setCellValue(row.getServiceFee().doubleValue());
                feeCell.setCellStyle(currencyStyle);

                Cell totalCell = excelRow.createCell(8);
                totalCell.setCellValue(row.getTotalPrice().doubleValue());
                totalCell.setCellStyle(currencyStyle);

                excelRow.createCell(9).setCellValue(row.getStatus());
                excelRow.createCell(10).setCellValue(row.getAirlineName());
            }

            // Summary row
            Row summaryRow = sheet.createRow(data.size() + 2);
            summaryRow.createCell(0).setCellValue("TỔNG CỘNG");

            Cell totalRevenueCell = summaryRow.createCell(8);
            totalRevenueCell.setCellFormula(
                "SUM(I2:I" + (data.size() + 1) + ")"
            );
            totalRevenueCell.setCellStyle(currencyStyle);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Freeze header row
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
```

### Audit Log — AOP Aspect

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditAspect {

    private final AdminAuditLogRepository auditLogRepository;
    private final HttpServletRequest httpRequest;

    // Tự động log tất cả method trong AdminController
    @AfterReturning(
        pointcut = "execution(* com.example.flighteasy.controller.AdminController.*(..))",
        returning = "result"
    )
    public void logAdminAction(JoinPoint joinPoint, Object result) {
        try {
            String adminEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();

            AdminAuditLog log = AdminAuditLog.builder()
                .adminEmail(adminEmail)
                .action(joinPoint.getSignature().getName())
                .ipAddress(httpRequest.getRemoteAddr())
                .createdAt(LocalDateTime.now())
                .build();

            auditLogRepository.save(log);
        } catch (Exception e) {
            // Không được phép làm fail business logic vì audit
            this.log.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}
```
