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

    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.expiresAt < :now")
    List<Booking> findExpiredPending(@Param("now")LocalDateTime now);

    List<Booking> findByUserIdOrderByCreatedAtDesc (Long userId);
}
