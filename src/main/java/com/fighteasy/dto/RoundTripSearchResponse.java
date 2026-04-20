package com.fighteasy.dto;

import java.math.BigDecimal;

public record RoundTripSearchResponse(
        FlightSearchResponse outbound,
        FlightSearchResponse returnTrip,
        FlightPair cheapestCombination
) {
    public record FlightPair(
            FlightSearchResult outboundFlight,
            FlightSearchResult returnFlight,
            BigDecimal totalCombinedPrice
    ) {}
}
