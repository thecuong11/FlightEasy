package com.flighteasy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePaymentResponse(
        String paymentUrl,
        String txnRef,
        BigDecimal amount,
        LocalDateTime expiresAt
) {
}
