# Hướng dẫn Implement Service 07 — Admin Dashboard & Báo cáo

> **Module:** Admin Service | **Stack:** Spring Boot + PostgreSQL + Redis + Apache POI  
> **Dựa theo spec:** `07-admin-dashboard.md` v1.0  
> **Phụ thuộc:** Tất cả Service 02-06 phải hoàn thành trước

---

## Tổng quan các bước

| Bước | Nội dung |
|------|----------|
| 1 | Database Schema (Views + Audit Log) |
| 2 | Dependency (Apache POI cho Excel) |
| 3 | DTO (KPI, Report) |
| 4 | DashboardService — KPI + Cache |
| 5 | ReportService — Export Excel |
| 6 | AdminBookingService — Quản lý booking |
| 7 | AdminAuditAspect — Tự động log hành động Admin |
| 8 | Controller |
| 9 | Test |

---

## Bước 1 — Database Schema

### 1.1 Materialized View doanh thu theo ngày

```sql
-- View tổng hợp — tính toán sẵn, query nhanh
CREATE MATERIALIZED VIEW daily_revenue AS
SELECT
    DATE(b.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS revenue_date,
    COUNT(b.id)                                           AS total_bookings,
    SUM(b.total_price)                                    AS total_revenue,
    AVG(b.total_price)                                    AS avg_ticket_price,
    COUNT(CASE WHEN b.status = 'CANCELLED' THEN 1 END)   AS cancelled_count,
    SUM(COALESCE(b.refund_amount, 0))                     AS total_refunded
FROM bookings b
WHERE b.status IN ('CONFIRMED', 'COMPLETED', 'CANCELLED')
GROUP BY DATE(b.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
WITH DATA;

-- Refresh mỗi đêm lúc 1:00 AM (dùng pg_cron hoặc Scheduler Java)
-- REFRESH MATERIALIZED VIEW daily_revenue;
```

### 1.2 View doanh thu theo chặng bay

```sql
CREATE VIEW revenue_by_route AS
SELECT
    ao.iata_code       AS origin,
    ad.iata_code       AS destination,
    al.name            AS airline,
    COUNT(b.id)        AS total_bookings,
    SUM(b.total_price) AS total_revenue,
    AVG(b.total_price) AS avg_price
FROM bookings b
JOIN booking_segments bs ON bs.booking_id = b.id
JOIN flight_classes fc   ON fc.id = bs.flight_class_id
JOIN flights f           ON f.id = fc.flight_id
JOIN airports ao         ON ao.id = f.origin_id
JOIN airports ad         ON ad.id = f.destination_id
JOIN airlines al         ON al.id = f.airline_id
WHERE b.status IN ('CONFIRMED', 'COMPLETED')
GROUP BY ao.iata_code, ad.iata_code, al.name;
```

### 1.3 Audit Log

```sql
CREATE TABLE admin_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(50),
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_admin ON admin_audit_logs(admin_id, created_at DESC);
CREATE INDEX idx_audit_entity ON admin_audit_logs(entity_type, entity_id);
```

### 1.4 Entity: AdminAuditLog

```java
@Entity
@Table(name = "admin_audit_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Column(nullable = false)
    private String action;        // CANCEL_BOOKING, UPDATE_FLIGHT_STATUS...

    private String entityType;    // BOOKING, FLIGHT, USER
    private String entityId;

    @Column(columnDefinition = "jsonb")
    private String oldValue;

    @Column(columnDefinition = "jsonb")
    private String newValue;

    private String ipAddress;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

---

## Bước 2 — Dependency (Apache POI)

```xml
<!-- Apache POI để tạo file Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

---

## Bước 2.5 — Thêm query vào FlightRepository

```java
public interface FlightRepository extends JpaRepository<Flight, Long> {

    // Đếm tổng chuyến bay trong ngày
    @Query("SELECT COUNT(f) FROM Flight f WHERE CAST(f.departureTime AS LocalDate) = :date")
    long countByDepartureDate(@Param("date") LocalDate date);

    // Đếm chuyến bay theo trạng thái trong ngày
    @Query("SELECT COUNT(f) FROM Flight f WHERE f.status = :status AND CAST(f.departureTime AS LocalDate) = :date")
    long countByStatusAndDepartureDate(@Param("status") FlightStatus status, @Param("date") LocalDate date);
}
```

---

## Bước 3 — DTO

```java
// KPI Dashboard
@Data @Builder
public class DashboardKPIResponse {
    private LocalDate date;

    // Doanh thu
    private BigDecimal todayRevenue;
    private BigDecimal yesterdayRevenue;
    private double revenueGrowthPercent;

    // Booking
    private long todayBookings;
    private long confirmedBookings;
    private long pendingBookings;
    private long cancelledBookings;
    private double conversionRate;

    // Chuyến bay
    private long totalFlights;
    private long delayedFlights;
    private long cancelledFlights;

    private BigDecimal avgTicketPrice;
    private LocalDateTime updatedAt;
}

// Báo cáo doanh thu theo ngày
@Data @Builder
public class RevenueChartData {
    private List<RevenuePoint> points;

    @Data @Builder
    public static class RevenuePoint {
        private LocalDate date;
        private BigDecimal revenue;
        private long bookings;
    }
}

// Top chặng bay
public record TopRouteResponse(
    String origin, String destination,
    String airline,
    long totalBookings,
    BigDecimal totalRevenue
) {}

// Booking report row (dùng cho Export Excel)
@Data @Builder
public class BookingReportRow {
    private String pnrCode;
    private LocalDate bookingDate;
    private String route;           // SGN → HAN
    private int passengerCount;
    private String classType;
    private BigDecimal subtotal;
    private BigDecimal serviceFee;
    private BigDecimal totalPrice;
    private String status;
    private String airlineName;
}

// Request Export
public record ReportRequest(
    @NotNull LocalDate fromDate,
    @NotNull LocalDate toDate,
    String type    // REVENUE | BOOKING | FLIGHT
) {}
```

---

## Bước 4 — Repository cho Dashboard

```java
// Dùng native query để lấy KPI nhanh
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Thống kê booking theo ngày
    @Query(value = """
        SELECT
            COUNT(*) AS total_bookings,
            COUNT(CASE WHEN status = 'CONFIRMED' OR status = 'COMPLETED' THEN 1 END) AS confirmed,
            COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending,
            COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled,
            COALESCE(SUM(CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN total_price END), 0) AS revenue,
            COALESCE(AVG(CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN total_price END), 0) AS avg_price
        FROM bookings
        WHERE DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') = :date
    """, nativeQuery = true)
    Map<String, Object> getDailyStats(@Param("date") LocalDate date);

    // Dữ liệu biểu đồ doanh thu
    @Query(value = """
        SELECT DATE(confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS date,
               SUM(total_price) AS revenue,
               COUNT(*) AS bookings
        FROM bookings
        WHERE status IN ('CONFIRMED', 'COMPLETED')
          AND confirmed_at >= :fromDate AND confirmed_at < :toDate
        GROUP BY DATE(confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
        ORDER BY date
    """, nativeQuery = true)
    List<Map<String, Object>> getRevenueChart(
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate
    );

    // Top chặng bay
    @Query(value = """
        SELECT ao.iata_code AS origin, ad.iata_code AS destination,
               al.name AS airline, COUNT(b.id) AS total_bookings,
               SUM(b.total_price) AS total_revenue
        FROM bookings b
        JOIN booking_segments bs ON bs.booking_id = b.id
        JOIN flight_classes fc ON fc.id = bs.flight_class_id
        JOIN flights f ON f.id = fc.flight_id
        JOIN airports ao ON ao.id = f.origin_id
        JOIN airports ad ON ad.id = f.destination_id
        JOIN airlines al ON al.id = f.airline_id
        WHERE b.status IN ('CONFIRMED', 'COMPLETED')
        GROUP BY ao.iata_code, ad.iata_code, al.name
        ORDER BY total_bookings DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Map<String, Object>> getTopRoutes(@Param("limit") int limit);

    // Data cho export Excel
    @Query("""
        SELECT new com.example.flighteasy.dto.admin.BookingReportRow(
            b.pnrCode,
            CAST(b.confirmedAt AS LocalDate),
            CONCAT(ao.iataCode, ' → ', ad.iataCode),
            SIZE(bs.passengers),
            fc.classType,
            b.subtotal,
            b.serviceFee,
            b.totalPrice,
            CAST(b.status AS string),
            al.name
        )
        FROM Booking b
        JOIN b.segments bs
        JOIN bs.flightClass fc
        JOIN fc.flight f
        JOIN f.origin ao
        JOIN f.destination ad
        JOIN f.airline al
        WHERE b.status IN ('CONFIRMED', 'COMPLETED')
          AND b.confirmedAt >= :fromDate AND b.confirmedAt <= :toDate
        ORDER BY b.confirmedAt DESC
    """)
    List<BookingReportRow> findForReport(
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate
    );
}
```

---

## Bước 5 — DashboardService

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // Chỉ đọc, không cần write lock
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KPI_CACHE_KEY = "admin:dashboard:kpis";
    private static final Duration KPI_TTL = Duration.ofMinutes(5);

    @SuppressWarnings("unchecked")
    public DashboardKPIResponse getDashboardKPIs() {
        // BR-02: Cache 5 phút
        Object cached = redisTemplate.opsForValue().get(KPI_CACHE_KEY);
        if (cached != null) {
            return (DashboardKPIResponse) cached;
        }

        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Query DB
        Map<String, Object> todayStats     = bookingRepository.getDailyStats(today);
        Map<String, Object> yesterdayStats = bookingRepository.getDailyStats(yesterday);

        BigDecimal todayRevenue     = toBigDecimal(todayStats.get("revenue"));
        BigDecimal yesterdayRevenue = toBigDecimal(yesterdayStats.get("revenue"));

        // Tính % tăng trưởng
        double growthPercent = yesterdayRevenue.compareTo(BigDecimal.ZERO) == 0
            ? 0
            : todayRevenue.subtract(yesterdayRevenue)
                .divide(yesterdayRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        long todayBookings    = toLong(todayStats.get("total_bookings"));
        long confirmedBookings = toLong(todayStats.get("confirmed"));
        double conversionRate = todayBookings == 0 ? 0
            : (double) confirmedBookings / todayBookings * 100;

        // Thống kê chuyến bay hôm nay
        long totalFlights    = flightRepository.countByDepartureDate(today);
        long delayedFlights  = flightRepository.countByStatusAndDepartureDate(FlightStatus.DELAYED, today);
        long cancelledFlights = flightRepository.countByStatusAndDepartureDate(FlightStatus.CANCELLED, today);

        DashboardKPIResponse response = DashboardKPIResponse.builder()
                .date(today)
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .revenueGrowthPercent(growthPercent)
                .todayBookings(todayBookings)
                .confirmedBookings(confirmedBookings)
                .pendingBookings(toLong(todayStats.get("pending")))
                .cancelledBookings(toLong(todayStats.get("cancelled")))
                .conversionRate(conversionRate)
                .totalFlights(totalFlights)
                .delayedFlights(delayedFlights)
                .cancelledFlights(cancelledFlights)
                .avgTicketPrice(toBigDecimal(todayStats.get("avg_price")))
                .updatedAt(LocalDateTime.now())
                .build();

        // Lưu cache
        redisTemplate.opsForValue().set(KPI_CACHE_KEY, response, KPI_TTL);
        return response;
    }

    public RevenueChartData getRevenueChart(String period) {
        LocalDate from = switch (period) {
            case "WEEKLY"  -> LocalDate.now().minusDays(7);
            case "MONTHLY" -> LocalDate.now().minusDays(30);
            case "YEARLY"  -> LocalDate.now().minusDays(365);
            default        -> LocalDate.now().minusDays(30);
        };

        List<Map<String, Object>> raw = bookingRepository.getRevenueChart(
            from.atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()
        );

        List<RevenueChartData.RevenuePoint> points = raw.stream()
            .map(row -> RevenueChartData.RevenuePoint.builder()
                .date(((java.sql.Date) row.get("date")).toLocalDate())
                .revenue(toBigDecimal(row.get("revenue")))
                .bookings(toLong(row.get("bookings")))
                .build())
            .toList();

        return RevenueChartData.builder().points(points).build();
    }

    public List<TopRouteResponse> getTopRoutes(int limit) {
        return bookingRepository.getTopRoutes(Math.min(limit, 20)).stream()
            .map(row -> new TopRouteResponse(
                (String) row.get("origin"),
                (String) row.get("destination"),
                (String) row.get("airline"),
                toLong(row.get("total_bookings")),
                toBigDecimal(row.get("total_revenue"))
            ))
            .toList();
    }

    // Helpers
    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }
}
```

---

## Bước 6 — ReportService (Export Excel)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final BookingRepository bookingRepository;

    public byte[] exportRevenueReport(ReportRequest request) throws IOException {
        List<BookingReportRow> data = bookingRepository.findForReport(
            request.fromDate().atStartOfDay(),
            request.toDate().atTime(23, 59, 59)
        );

        return buildExcelFile(data, request);
    }

    private byte[] buildExcelFile(List<BookingReportRow> data, ReportRequest request)
            throws IOException {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Báo cáo doanh thu");

            // ===== STYLE =====
            // Header style — nền xanh, chữ trắng, in đậm
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.index);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.index);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Currency style — format tiền tệ VNĐ
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("#,##0"));

            // Alternating row style — xen kẽ màu nền
            CellStyle altStyle = workbook.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.index);
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ===== HEADER ROW =====
            String[] headers = {
                "STT", "Mã PNR", "Ngày đặt", "Chặng bay",
                "Số HK", "Hạng vé", "Giá vé (VNĐ)", "Phí DV (VNĐ)",
                "Tổng tiền (VNĐ)", "Trạng thái", "Hãng bay"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ===== DATA ROWS =====
            for (int i = 0; i < data.size(); i++) {
                BookingReportRow row = data.get(i);
                Row excelRow = sheet.createRow(i + 1);

                excelRow.createCell(0).setCellValue(i + 1);
                excelRow.createCell(1).setCellValue(row.getPnrCode());
                excelRow.createCell(2).setCellValue(
                    row.getBookingDate() != null ? row.getBookingDate().toString() : ""
                );
                excelRow.createCell(3).setCellValue(row.getRoute());
                excelRow.createCell(4).setCellValue(row.getPassengerCount());
                excelRow.createCell(5).setCellValue(row.getClassType());

                // Cột tiền tệ
                createCurrencyCell(excelRow, 6, row.getSubtotal(), currencyStyle);
                createCurrencyCell(excelRow, 7, row.getServiceFee(), currencyStyle);
                createCurrencyCell(excelRow, 8, row.getTotalPrice(), currencyStyle);

                excelRow.createCell(9).setCellValue(row.getStatus());
                excelRow.createCell(10).setCellValue(row.getAirlineName());
            }

            // ===== SUMMARY ROW =====
            int summaryRowIdx = data.size() + 2;
            Row summaryRow = sheet.createRow(summaryRowIdx);
            summaryRow.createCell(0).setCellValue("TỔNG CỘNG");

            // SUM formula cho cột tổng tiền
            Cell totalCell = summaryRow.createCell(8);
            totalCell.setCellFormula("SUM(I2:I" + (data.size() + 1) + ")");
            totalCell.setCellStyle(currencyStyle);

            // ===== FORMAT =====
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);   // Freeze header row

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createCurrencyCell(Row row, int colIdx, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }
}
```

---

## Bước 7 — AdminAuditAspect (AOP)

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditAspect {

    private final AdminAuditLogRepository auditLogRepository;
    private final HttpServletRequest httpRequest;  // Spring tự inject request-scoped bean
    private final ObjectMapper objectMapper;

    // Tự động log sau khi method Admin thực thi thành công
    @AfterReturning(
        pointcut = "execution(* com.example.flighteasy.controller.AdminController.*(..))",
        returning = "result"
    )
    public void logAdminAction(JoinPoint joinPoint, Object result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return;

            String adminEmail = auth.getName();
            String methodName = joinPoint.getSignature().getName();

            AdminAuditLog auditLog = AdminAuditLog.builder()
                .action(methodName)
                .ipAddress(getClientIp())
                .createdAt(LocalDateTime.now())
                .build();

            // Lấy user từ email nếu cần
            // auditLog.setAdmin(userRepository.findByEmail(adminEmail).orElse(null));

            auditLogRepository.save(auditLog);
            log.debug("Admin action logged: {} by {}", methodName, adminEmail);

        } catch (Exception e) {
            // QUAN TRỌNG: Không được throw exception ở đây — không được làm fail business logic
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    private String getClientIp() {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        return ip != null ? ip.split(",")[0].trim() : httpRequest.getRemoteAddr();
    }
}
```

Bật AOP trong main class:

```java
@SpringBootApplication
@EnableAspectJAutoProxy  // Bật AOP proxy
@EnableScheduling        // Bật scheduler cho refresh materialized view
public class Application { ... }
```

**Thêm dependency AOP:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## Bước 7.5 — Repository

```java
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);
}
```

---

## Bước 8 — Controller

```java
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")   // BR-01: Chỉ Admin
public class AdminController {

    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final BookingService bookingService;

    // ===== DASHBOARD =====

    @GetMapping("/dashboard/kpis")
    public ResponseEntity<DashboardKPIResponse> getDashboardKPIs() {
        return ResponseEntity.ok(dashboardService.getDashboardKPIs());
    }

    @GetMapping("/dashboard/revenue-chart")
    public ResponseEntity<RevenueChartData> getRevenueChart(
            @RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(dashboardService.getRevenueChart(period));
    }

    @GetMapping("/dashboard/top-routes")
    public ResponseEntity<List<TopRouteResponse>> getTopRoutes(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getTopRoutes(limit));
    }

    // ===== EXPORT =====

    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@Valid @RequestBody ReportRequest request)
            throws IOException {
        byte[] excelBytes = reportService.exportRevenueReport(request);

        String filename = "bao-cao-" + request.fromDate() + "-den-" + request.toDate() + ".xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excelBytes);
    }

    // ===== BOOKING MANAGEMENT =====

    @GetMapping("/bookings")
    public ResponseEntity<Page<BookingResponse>> getAllBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.getAllBookings(status, page, size));
    }

    // Cần thêm method cancelBookingByAdmin vào BookingService:
    // @Transactional
    // public void cancelBookingByAdmin(String pnrCode, String reason) {
    //     Booking booking = bookingRepository.findByPnrCode(pnrCode)
    //             .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));
    //     booking.setStatus(BookingStatus.CANCELLED);
    //     booking.setCancelledAt(LocalDateTime.now());
    //     booking.setCancelReason(reason);
    //     bookingRepository.save(booking);
    //     releaseSeatsForBooking(booking);
    // }

    @PatchMapping("/bookings/{pnr}/cancel")
    public ResponseEntity<Void> cancelBookingByAdmin(
            @PathVariable String pnr,
            @RequestParam(required = false) String reason) {
        bookingService.cancelBookingByAdmin(pnr, reason);
        return ResponseEntity.ok().build();
    }
}
```

---

## Bước 9 — Test bằng Postman

| # | Request | Kết quả mong đợi |
|---|---------|-----------------|
| 1 | `GET /admin/dashboard/kpis` với token Admin | `200` + KPI data |
| 2 | `GET /admin/dashboard/kpis` lần 2 | Response nhanh hơn (từ Redis cache) |
| 3 | `GET /admin/dashboard/revenue-chart?period=MONTHLY` | Dữ liệu 30 ngày |
| 4 | `GET /admin/dashboard/top-routes?limit=5` | Top 5 chặng bay |
| 5 | `POST /admin/reports/export` với dateRange | File `.xlsx` download |
| 6 | Gọi endpoint Admin với token User thường | `403 Forbidden` |
| 7 | Gọi endpoint Admin → Kiểm tra bảng `admin_audit_logs` | Có bản ghi mới |

### Mẫu request Export:

```json
POST /api/v1/admin/reports/export
{
  "fromDate": "2025-06-01",
  "toDate": "2025-06-30",
  "type": "REVENUE"
}
```

---

## Lưu ý quan trọng

- `@PreAuthorize("hasRole('ROLE_ADMIN')")` ở class level áp dụng cho TẤT CẢ method trong controller
- **Materialized View** (`daily_revenue`) cần được refresh định kỳ — dùng `@Scheduled` hoặc PostgreSQL cron job
- AOP Audit aspect phải **không bao giờ** throw exception để tránh làm fail business logic
- File Excel lớn (>10,000 rows) nên xử lý `@Async` và trả về jobId để FE polling — tránh timeout
- Redis cache KPI 5 phút — nếu muốn force refresh, có thể thêm endpoint `DELETE /admin/cache/kpis`
