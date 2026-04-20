package com.fighteasy.repository;

import com.fighteasy.dto.FlightSearchResult;
import com.fighteasy.entity.Flight;
import com.fighteasy.enums.FlightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    @Query("SELECT COUNT(f) > 0 FROM Flight f WHERE f.flightNumber = :flightNumber " +
            "AND CAST(f.departureTime AS LocalDate) = :date AND f.id != :excludeId")
    boolean existsByFlightNumberAndDate(String flightNumber, LocalDate date, Long excludeId);

    List<Flight> findByStatus(FlightStatus status);

    @Query()
    List<FlightSearchResult> searchFlight(
            @Param("originIata")
            String originIata,
            @Param("destIata")
            String destIata,
            @Param("departureDate")
            LocalDate departureDate,
            @Param("classType")
            String classType,
            @Param("passengerCount")
            int passengerCount
    );
}
