package com.flighteasy.dto;


import lombok.Builder;

import java.util.List;

@Builder
public record SeatMapResponse(
        List<SeatRow> firstClass,
        List<SeatRow> business,
        List<SeatRow> economy
) {}
