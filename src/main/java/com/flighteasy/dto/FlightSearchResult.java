package com.flighteasy.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
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

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime departureTime;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime arrivalTime;
    private Integer durationMinutes;

    private BigDecimal pricePerPerson;
    private BigDecimal totalPrice;
    private Integer availableSeats;
    private Integer baggageAllowanceKg;
    private Boolean isRefundable;
    private String status;
    private List<String> tags = new ArrayList<>();

    public FlightSearchResult(Long id, String flightNumber, String airlineCode, String airLineName, String airlineLogoUrl, String originIata,
                              String originName, String originCity, String destinationIata, String destinationName, String destinationCity,
                              LocalDateTime departureTime, LocalDateTime arrivalTime, Integer durationMinutes, BigDecimal pricePerPerson,
                              Integer availableSeats, Integer baggageAllowanceKg, Boolean isRefundable, String status){
        this.id = id;
        this.flightNumber = flightNumber;
        this.airlineCode = airlineCode;
        this.airLineName = airLineName;
        this.airlineLogoUrl = airlineLogoUrl;
        this.originIata = originIata;
        this.originName = originName;
        this.originCity = originCity;
        this.destinationIata = destinationIata;
        this.destinationName = destinationName;
        this.destinationCity = destinationCity;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.durationMinutes = durationMinutes;
        this.pricePerPerson = pricePerPerson;
        this.availableSeats = availableSeats;
        this.baggageAllowanceKg = baggageAllowanceKg;
        this.isRefundable = isRefundable;
        this.status = status;
        this.tags = new ArrayList<>();
    }

    public void addTag(String tag){
        this.tags.add(tag);
    }

    public FlightSearchResult() {
        this.tags = new ArrayList<>();
    }
}
