package com.fighteasy.dto;

import com.fighteasy.enums.ClassType;
import com.fighteasy.enums.SortBy;
import com.fighteasy.enums.TimeRange;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class FlightSearchRequest {

    @NotBlank
    private String from;
    @NotBlank
    private String to;
    @NotNull
    private LocalDate departDate;

    @Min(1)
    private int adults = 1;
    @Min(0)
    private int children = 0;
    @Min(0)
    private int infants = 0;

    private LocalDate returnDate;
    private ClassType classType = ClassType.ECONOMY;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> airlines;
    private Integer maxDuration;
    private TimeRange departTimeRange;

    private SortBy sortBy = SortBy.PRICE_ASC;
    private int page = 0;
    private int size = 20;

    public int getTotalPassengers() {return adults + children;}
}
