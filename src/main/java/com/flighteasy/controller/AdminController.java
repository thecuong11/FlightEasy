package com.flighteasy.controller;

import com.flighteasy.dto.*;
import com.flighteasy.service.BookingService;
import com.flighteasy.service.DashboardService;
import com.flighteasy.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final BookingService bookingService;

    @GetMapping("/dashboard/kpis")
    public ResponseEntity<DashboardKPIResponse> getDashboardKPIs() {
        return ResponseEntity.ok(dashboardService.getDashboardKPIs());
    }

    @GetMapping("/dashboard/revenue-chart")
    public ResponseEntity<RevenueChartData> getRevenueChart(@RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(dashboardService.getRevenueChart(period));
    }

    @GetMapping("/dashboard/top-routes")
    public ResponseEntity<List<TopRouteResponse>> getTopRoutes(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getTopRoutes(limit));
    }

    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@Valid @RequestBody ReportRequest request) throws IOException {
        byte[] excelBytes = reportService.exportRevenueReport(request);

        String filename = "bao-cao-" + request.fromDate() + "-den-" + request.toDate() + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(excelBytes);
    }

    @GetMapping("/bookings")
    public ResponseEntity<Page<BookingResponse>> getAllBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.getAllBookings(status, page, size));
    }

    @PatchMapping("/bookings/{pnr}/cancel")
    public ResponseEntity<Void> cancelBookingByAdmin(
            @PathVariable String pnr,
            @RequestParam(required = false) String reason) {
        bookingService.cancelBookingByAdmin(pnr, reason);
        return ResponseEntity.ok().build();
    }
}
