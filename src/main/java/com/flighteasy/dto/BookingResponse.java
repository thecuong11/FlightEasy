package com.flighteasy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        String pnrCode,
        String status,
        LocalDateTime expiresAt,
        FlightInfo flight,
        List<PassengerInfo> passengers,
        PricingInfo pricing,
        LocalDateTime paymentDeadline

) {
    public record FlightInfo(
            String flightNumber, String from, String to, LocalDateTime departureTime
    ) {}

    public record PassengerInfo(
            String name, String seat, String idNumber
    ) {}

    public record PricingInfo(
            BigDecimal subtotal, BigDecimal serviceFee,
            BigDecimal totalPrice, String currency
    ) {}
}
