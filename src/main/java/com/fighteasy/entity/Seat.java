package com.fighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "seats")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false, length = 5)
    private String seatNumber;

    @Column(nullable = false)
    private String classType;

    @Column(nullable = false)
    private String position;

    private Integer rowNumber;
    private Boolean isAvailable = true;
    private Boolean isExtraLegroom = false;
    private BigDecimal extraFee = BigDecimal.ZERO;
}
