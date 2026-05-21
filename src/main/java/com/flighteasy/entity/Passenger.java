package com.flighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "passengers")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_segment_id", nullable = false)
    private BookingSegment bookingSegment;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDateTime dateOfBirth;

    private String gender;

    @Column(length = 2, nullable = false)
    private String nationality;

    @Column(nullable = false)
    private String idType;

    @Column(nullable = false)
    private String idNumber;

    private LocalDate idExpiry;

    private String passengerType = "ADULT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    private Integer extraBaggageKg = 0;
    private String mealPreference;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
