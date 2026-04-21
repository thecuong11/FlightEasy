package com.flighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "airlines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2, unique = true, nullable = false)
    private String iataCode;

    @Column(length = 3)
    private String icaoCode;

    @Column(nullable = false)
    private String name;

    private String country;
    private String logoUrl;
    private boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
