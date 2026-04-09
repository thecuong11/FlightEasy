package com.fighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "flight_classes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false)
    private String classType;

    @Column(nullable = false)
    private BigDecimal basePrice;

    private Integer totalSeats;
    private Integer availableSeats;
    private Integer baggageAllowanceKg = 20;
    private Integer carryOnKg = 7;
    private Boolean isRefundable = true;
    private Integer refundFeePercent = 0;
}
