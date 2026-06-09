package com.flighteasy.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record SeatInfo(
        String seatNumber, String position,
        Boolean isAvailable, BigDecimal extraFee, Boolean isExtraLegroom
) {}
