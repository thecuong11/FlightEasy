package com.flighteasy.repository;

import com.flighteasy.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPnrCode(String pnrCode);
    boolean existsByPnrCode(String pnrCode);

    @Query("""
        SELECT DISTINCT b FROM Booking b 
        LEFT JOIN FETCH b.segments s 
        LEFT JOIN FETCH s.passengers p
        LEFT JOIN FETCH p.seat
        LEFT JOIN FETCH s.flightClass fc
        LEFT JOIN FETCH fc.flight
        WHERE b.status = 'PENDING'
        AND b.expiresAt < :now
        """)
    List<Booking> findExpiredPending(@Param("now")LocalDateTime now);

    List<Booking> findByUserIdOrderByCreatedAtDesc (Long userId);

    @Query("""
        SELECT DISTINCT b FROM Booking b
        LEFT JOIN FETCH b.segments s
        LEFT JOIN FETCH s.passengers p
        LEFT JOIN FETCH p.seat
        LEFT JOIN FETCH s.flightClass fc
        WHERE b.id = :id
""")
    Optional<Booking> findByIdWithSegmentsAndPassengers(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT b FROM Booking b
        JOIN b.segments s
        JOIN s.flightClass fc
        JOIN fc.flight f
        WHERE b.status = 'CONFIRMED'
        AND f.departureTime >= :start
        AND f.departureTime < :end
""")
    List<Booking> findConfirmedBookingsForCheckin(
            @Param("start")
            LocalDateTime start,
            @Param("end")
            LocalDateTime end
    );
}
