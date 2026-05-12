package com.flighteasy.dto;

import com.flighteasy.enums.ClassType;
import com.flighteasy.enums.SortBy;
import com.flighteasy.enums.TimeRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class FlightSearchRequest {

    @NotBlank
    private String from;
    @NotBlank
    private String to;
    @NotNull
    private LocalDate departDate;

    private Integer adults = 1;
    private Integer children = 0;
    private Integer infants = 0;

    private LocalDate returnDate;
    private ClassType classType = ClassType.ECONOMY;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> airlines;
    private Integer maxDuration;
    private TimeRange departTimeRange;

    private SortBy sortBy = SortBy.PRICE_ASC;
    private Integer page = 0;
    private Integer size = 20;

    public int getAdults() { return adults != null ? adults : 1; }
    public int getChildren() { return children != null ? children : 0; }
    public int getInfants() { return infants != null ? infants : 0; }
    public int getPage() { return  page != null ? page : 0; }
    public int getSize() { return size != null ? Math.min(size, 50) : 20; }
    public SortBy getSortBy() { return sortBy != null ? sortBy : SortBy.PRICE_ASC; }
    public ClassType getClassType() { return classType != null ? classType : ClassType.ECONOMY; }

    public int getTotalPassengers() {return adults + children;}
}
