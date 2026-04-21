package com.flighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "airports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Airport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 3,unique = true, nullable = false)
    private String iataCode;

    @Column(length = 4)
    private String icaoCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @Column(length = 2, nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String timezone;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
