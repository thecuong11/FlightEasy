package com.fighteasy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FlightResponse(
        Long id,
        String flightNumber,
        AirlineInfo airline,
        AirportInfo origin,
        AirportInfo destination,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer durationMinutes,
        String status,
        String terminal,
        String gate,
        List<FlightClassInfo> classes
) {
    public record AirlineInfo(String code, String name, String logoUrl) {}
    public record AirportInfo(String iata, String name, String city) {}
    public record FlightClassInfo(String classType, BigDecimal basePrice, Integer availableSeats, Integer totalSeats, Integer baggageAllowanceKg) {}
}
