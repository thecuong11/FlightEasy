package com.flighteasy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingRequest(
        @NotNull Long flightClassId,
        @NotBlank String contactEmail,
        String contactPhone,
        @NotEmpty List<PassengerRequest> passengers,
        List<Long> selectedSeatIds
        ) {
    public int getNonInfantPassengers() {
        return (int) passengers.stream()
                .filter(p -> !"INFANT".equals(p.passengerType()))
                .count();
    }

    public List<String> getPassengerIdNumbers() {
        return passengers.stream().map(PassengerRequest::idNumber).toList();
    }
}
