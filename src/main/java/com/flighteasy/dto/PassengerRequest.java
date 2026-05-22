package com.flighteasy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PassengerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull LocalDateTime dateOfBirth,
        String gender,
        @NotBlank String nationality,
        @NotBlank String idType,
        @NotBlank String idNumber,
        LocalDate idExpiry,
        String passengerType,
        Long seatId,
        Integer extraBaggageKg,
        String mealPreference
        ) {
}
