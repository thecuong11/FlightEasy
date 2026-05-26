package com.flighteasy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
        String pnrCode,
        String status,
        BigDecimal amount,
        String bankCode,
        LocalDateTime paidAt
) {
}
