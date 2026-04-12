package com.fighteasy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateFlightRequest(
        @NotBlank
        String flightNumber,
        @NotNull
        Long airlineId,
        Long aircraftTypeId,
        @NotBlank
        String originIata,
        @NotBlank
        String destinationIata,
        @NotNull
        LocalDateTime departureTime,
        @NotNull
        LocalDateTime arrivalTime,
        String terminal,
        String gate,
        @NotEmpty
        List<CreateFlightClassRequest> classes
) {
}
