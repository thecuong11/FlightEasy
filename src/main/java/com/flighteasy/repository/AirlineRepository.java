package com.flighteasy.repository;

import com.flighteasy.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {
    Optional<Airline> findByIataCode(String iataCode);
    List<Airline> findByIsActiveTrue();
    boolean existsByIataCode(String iataCode);
}
