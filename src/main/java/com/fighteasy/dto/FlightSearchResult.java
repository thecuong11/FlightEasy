package com.fighteasy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FlightSearchResult {

    private Long id;
    private String flightNumber;
    private String airlineCode;
    private String airLineName;
    private String airlineLogoUrl;

    private String originIata;
    private String originName;
    private String originCity;
    private String destinationIata;
    private String destinationName;
    private String destinationCity;

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer durationMinutes;

    private BigDecimal pricePerPerson;
    private BigDecimal totalPrice;

    private Integer availableSeats;
    private Integer baggageAllowanceKg;
    private Boolean isRefundable;

    private String status;
    private List<String> tags = new ArrayList<>();

    public void addTag(String tag){
        this.tags.add(tag);
    }
}
