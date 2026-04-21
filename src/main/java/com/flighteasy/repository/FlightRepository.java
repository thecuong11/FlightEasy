package com.flighteasy.repository;

import com.flighteasy.dto.FlightSearchResult;
import com.flighteasy.entity.Flight;
import com.flighteasy.enums.FlightStatus;
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

    @Query("""
        SELECT new com.flighteasy.dto.FlightSearchResult(
                    f.id, f.flightNumber,
                    al.iataCode, al.name, al.logoUrl,
                    ao.iataCode, ao.name, ao.city,
                    ad.iataCode, ad.name, ad.city,
                    f.departureTime, f.arrivalTime, f.durationMinutes,
                    fc.basePrice, fc.availableSeats, fc.baggageAllowanceKg,
                    fc.isRefundable, CAST(f.status AS string)
                )
                FROM Flight f
                JOIN f.airline al
                JOIN f.origin ao
                JOIN f.destination ad
                JOIN f.flightClasses fc
                WHERE ao.iataCode = :originIata
                  AND ad.iataCode = :destIata
                  AND CAST(f.departureTime AS LocalDate) = :departureDate
                  AND f.status IN ('SCHEDULED', 'DELAYED')
                  AND fc.classType = :classType
                  AND fc.availableSeats >= :passengerCount
""")
    List<FlightSearchResult> searchFlights(
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
