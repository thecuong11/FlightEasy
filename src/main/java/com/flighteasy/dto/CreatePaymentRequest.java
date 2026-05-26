package com.flighteasy.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(
        @NotBlank
        String pnrCode,
        @NotBlank
        String returnUrl,
        String ipAddress
) {
}
