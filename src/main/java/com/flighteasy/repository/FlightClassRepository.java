package com.flighteasy.repository;

import com.flighteasy.entity.FlightClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightClassRepository extends JpaRepository<FlightClass, Long> {
    List<FlightClass> findByFlightId(Long flightId);
}
