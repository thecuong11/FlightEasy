package com.flighteasy.controller;

import com.flighteasy.dto.BookingResponse;
import com.flighteasy.dto.CancelBookingResponse;
import com.flighteasy.dto.CreateBookingRequest;
import com.flighteasy.dto.SeatMapResponse;
import com.flighteasy.entity.User;
import com.flighteasy.service.BookingService;
import com.flighteasy.service.SeatMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final SeatMapService seatMapService;

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> getBooking(@Valid @RequestBody CreateBookingRequest request, @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request, user.getId()));
    }

    @GetMapping("/bookings/{pnr}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable String pnr, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getBooking(pnr, user.getId()));
    }

    @DeleteMapping("/bookings/{pnr}")
    public ResponseEntity<CancelBookingResponse> cancelBooking(@PathVariable String pnr, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.cancelBooking(pnr, user.getId()));
    }

    @GetMapping("/flights/{flightId}/seats")
    public ResponseEntity<SeatMapResponse> getSeatMap(@PathVariable Long flightId) {
        return ResponseEntity.ok(seatMapService.getSeatMap(flightId));
    }
}
