package com.flighteasy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class DashboardKPIResponse {
    private LocalDate date;

    private BigDecimal yesterdayRevenue;
    private BigDecimal todayRevenue;
    private double revenueGrowthPercent;

    private long todayBookings;
    private long confirmedBookings;
    private long pendingBookings;
    private long cancelledBookings;
    private double conversionRate;

    private long totalFlights;
    private long delayedFlight;
    private long cancelledFlights;

    private BigDecimal avgTicketPrice;
    private LocalDateTime updatedAt;
}
