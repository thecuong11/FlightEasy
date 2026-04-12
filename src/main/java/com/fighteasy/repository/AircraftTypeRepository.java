package com.fighteasy.repository;

import com.fighteasy.entity.AircraftType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AircraftTypeRepository extends JpaRepository<AircraftType, Long> {
    Optional<AircraftType> findByCode(String code);
}
