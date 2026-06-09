package com.flighteasy.dto;


import lombok.Builder;

import java.util.List;

@Builder
public record SeatRow(int rowNumber, List<SeatInfo> seats) {}
