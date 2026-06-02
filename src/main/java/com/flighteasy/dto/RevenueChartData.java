package com.flighteasy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class RevenueChartData {
    private List<RevenuePoint> points;

    @Data
    @Builder
    public static class RevenuePoint {
        private LocalDate date;
        private BigDecimal revenue;
        private long bookings;
    }
}
