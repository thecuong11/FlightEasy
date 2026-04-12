package com.fighteasy.dto;

import com.fighteasy.enums.FlightStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFlightStatusRequest(
        @NotNull
        FlightStatus status,
        Integer delayMinutes,
        String reason
) {
}
