package com.flighteasy.event;

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

    @EventListener
    @Async("emailTaskExecutor")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Sending confirmed email for booking {}", event.getBooking().getPnrCode());
        emailService.sendBookingConfirmation(event.getBooking());
    }

    @EventListener
    @Async("emailTaskExecutor")
    public void onBookingCancelled(BookingCancelledEvent event) {
        emailService.sendBookingCancellation(event.getBooking());
    }

    @EventListener
    @Async("emailTaskExecutor")
    public void onFlightCancelled(FlightCancelledEvent event) {
        log.info("Flight {} cancelled - sending notifications", event.getFlight().getFlightNumber());
    }
}
