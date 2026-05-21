package com.flighteasy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FlightSearchResponse(SearchMeta meta, List<FlightSearchResult> flights, PriceRange priceRange, AvailableFilters availableFilters) {
    public record SearchMeta(
            String from, String to,
            LocalDate departDate,
            int adults, int children, int infants, String classType
    ) {}

    public record PriceRange(BigDecimal min, BigDecimal max ){}

    public record AvailableFilters(List<String> airlines, DurationRange durationRange ){}

    public record DurationRange(int min, int max){}

    public record RoundTripSearchResponse(FlightSearchResponse outbound, FlightSearchResponse returnTrip, FlightPair cheapestCombination) {
        public record FlightPair(
                FlightSearchResult outboundFlight,
                FlightSearchResult returnFlight,
                BigDecimal totalCombinedPrice
        ) {}
    }
}
