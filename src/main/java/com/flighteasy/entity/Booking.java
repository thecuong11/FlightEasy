package com.flighteasy.entity;

import com.flighteasy.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 6, unique = true, nullable = false)
    private String pnrCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;

    private String tripType = "ONE_WAY";

    @Column(nullable = false)
    private BigDecimal subtotal;

    private BigDecimal serviceFee =  BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    private String currency = "VND";

    @Column(nullable = false)
    private String contactEmail;

    private String contactPhone;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private BigDecimal refundAmount;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @Builder.Default
    private List<BookingSegment> bookingSegments = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
