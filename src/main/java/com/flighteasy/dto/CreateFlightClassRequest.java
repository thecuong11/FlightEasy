package com.flighteasy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateFlightClassRequest(
        @NotBlank
        String classType,
        @NotNull
        BigDecimal basePrice,
        @NotNull
        Integer totalSeats,
        Integer baggageAllowanceKg,
        Boolean isRefundable,
        Integer refundFeePercent
) {
}
