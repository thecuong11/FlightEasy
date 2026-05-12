package com.flighteasy.controller;

import com.flighteasy.dto.FlightSearchRequest;
import com.flighteasy.dto.FlightSearchResponse;
import com.flighteasy.dto.RoundTripSearchResponse;
import com.flighteasy.service.FlightSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
public class FlightSearchController {

    private final FlightSearchService searchService;

    @GetMapping("/search")
    public ResponseEntity<FlightSearchResponse> search(@Valid FlightSearchRequest request){
        return ResponseEntity.ok(searchService.search(request));
    }

    @GetMapping("/search/round-trip")
    public ResponseEntity<RoundTripSearchResponse> searchRoundTrip(@Valid FlightSearchRequest request){
        return ResponseEntity.ok(searchService.searchRoundTrip(request));
    }
}
