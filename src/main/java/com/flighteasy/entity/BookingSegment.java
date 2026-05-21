package com.flighteasy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking_segments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_class_id", nullable = false)
    private FlightClass flightClass;

    private String segmentType = "OUTBOUND";
    private BigDecimal segmentPrice;

    @OneToMany(mappedBy = "bookingSegment", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Passenger> passengers = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
