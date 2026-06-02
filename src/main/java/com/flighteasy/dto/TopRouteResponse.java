package com.flighteasy.dto;

import java.math.BigDecimal;

public record TopRouteResponse(
        String origin,
        String destination,
        String airline,
        long totalBookings,
        BigDecimal totalRevenue
) {
}
