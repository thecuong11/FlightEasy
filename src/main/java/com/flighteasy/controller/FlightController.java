package com.flighteasy.controller;

import com.flighteasy.dto.CreateFlightRequest;
import com.flighteasy.dto.FlightResponse;
import com.flighteasy.dto.UpdateFlightStatusRequest;
import com.flighteasy.entity.Airport;
import com.flighteasy.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @GetMapping("/api/v1/airports")
    public ResponseEntity<List<Airport>> getAllAirport(){
        return ResponseEntity.ok(flightService.getAllAirport());
    }

    @GetMapping("/api/v1/airports/{iata}")
    public ResponseEntity<Airport> getAirport(@PathVariable String iata){
        return ResponseEntity.ok(flightService.getAirportByIata(iata));
    }

    @GetMapping("/api/v1/flights/{id}")
    public ResponseEntity<FlightResponse> getFlight(@PathVariable Long id){
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    @PostMapping("/api/v1/admin/airports")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Airport> createAirport(@Valid @RequestBody Airport airport){
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.createAirport(airport));
    }

    @PostMapping("/api/v1/admin/flights")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<FlightResponse> createFlight(@Valid @RequestBody CreateFlightRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.createFlight(request));
    }

    @PatchMapping("/api/v1/admin/flights/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateFlightStatusRequest request){
        flightService.updateFlightStatus(id, request);
        return ResponseEntity.ok().build();
    }
}
