package com.flighteasy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CancelBookingResponse(
        String pnrCode,
        BigDecimal refundAmount,
        LocalDateTime cancelledAt
) {
}
