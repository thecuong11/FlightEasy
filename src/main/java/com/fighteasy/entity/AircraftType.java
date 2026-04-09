package com.fighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "aircraft_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AircraftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private Integer totalSeats;
    private Integer economySeats;
    private Integer businessSeats = 0;
    private Integer firstClassSeats = 0;
}
