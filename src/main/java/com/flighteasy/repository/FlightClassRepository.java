package com.flighteasy.repository;

import com.flighteasy.entity.FlightClass;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FlightClassRepository extends JpaRepository<FlightClass, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fc FROM FlightClass fc WHERE fc.id = :id")
    Optional<FlightClass> findByIdWithLock(@Param("id") Long id);
}