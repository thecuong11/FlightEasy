package com.fighteasy.repository;

import com.fighteasy.entity.Flight;
import com.fighteasy.enums.FlightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    @Query("SELECT COUNT(f) > 0 FROM Flight f WHERE f.flightNumber = :flightNumber " +
            "AND CAST(f.departureTime AS LocalDate) = :date AND f.id != excludeId")
    boolean existsByFlightNumberAndDate(String flightNumber, LocalDate date, Long excludeId);

    List<Flight> findByStatus(FlightStatus status);
}
