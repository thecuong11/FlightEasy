package com.fighteasy.repository;

import com.fighteasy.entity.FlightClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightClassRepository extends JpaRepository<FlightClass, Long> {
    List<FlightClass> findByFlightId(Long flightId);
}
