package com.flighteasy.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReportRequest(
        @NotNull
        LocalDate fromDate,
        @NotNull
        LocalDate toDate,
        String type
) {
}
