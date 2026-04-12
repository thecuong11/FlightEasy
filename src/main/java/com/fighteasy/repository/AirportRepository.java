package com.fighteasy.repository;

import com.fighteasy.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {
    Optional<Airport> findByIataCode(String iataCode);
    boolean existsByIataCode(String iataCode);
    List<Airport> findByIsActiveTrue();
}
