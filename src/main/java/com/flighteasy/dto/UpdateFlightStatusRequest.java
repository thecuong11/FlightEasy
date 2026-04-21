package com.flighteasy.dto;

import com.flighteasy.enums.FlightStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFlightStatusRequest(
        @NotNull
        FlightStatus status,
        Integer delayMinutes,
        String reason
) {
}
