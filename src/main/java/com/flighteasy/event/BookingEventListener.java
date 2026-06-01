package com.flighteasy.event;

import com.flighteasy.entity.Booking;
import com.flighteasy.repository.BookingRepository;
import com.flighteasy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final EmailService emailService;
    private final BookingRepository bookingRepository;

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        Booking booking = bookingRepository
                .findByIdWithSegmentsAndPassengers(event.getBooking().getId())
                        .orElse(null);

        if (booking == null) return;

        log.info("Sending confirmed email for booking {}", event.getBooking().getPnrCode());
        emailService.sendBookingConfirmation(event.getBooking());
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        Booking booking = bookingRepository
                .findByIdWithSegmentsAndPassengers(event.getBooking().getId())
                        .orElse(null);

        if (booking == null) return;

        emailService.sendBookingCancellation(event.getBooking());
    }

    @EventListener
    @Async("emailTaskExecutor")
    public void onFlightCancelled(FlightCancelledEvent event) {
        log.info("Flight {} cancelled - sending notifications", event.getFlight().getFlightNumber());
    }
}
