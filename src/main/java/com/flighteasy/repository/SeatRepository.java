package com.flighteasy.repository;

import com.flighteasy.entity.Seat;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :ids")
    List<Seat> findAllByIdWithLock(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE Seat s SET s.isAvailable = false WHERE s.id IN :ids")
    void markAsHeld(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE Seat s SET s.isAvailable = true WHERE s.id IN :ids")
    void releaseSeats(@Param("ids") List<Long> ids);

    List<Seat> findByFlightId(Long flightId);
}
