package com.flighteasy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BookingReportRow {
    private String pnrCode;
    private LocalDate bookingDate;
    private String route;
    private int passengerCount;
    private String classType;
    private BigDecimal subtotal;
    private BigDecimal serviceFee;
    private BigDecimal totalPrice;
    private String status;
    private String airlineName;
}
