package com.flighteasy.event;

import com.flighteasy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightCancelledListener {

    private final EmailService emailService;

    @EventListener
    public void onFlightCancelled(FlightCancelledEvent event) {
        log.info("Flight {} cancelled - notifying {} affected bookings",
                event.getFlight().getFlightNumber(), event.getAffectedBookings().size());

        emailService.sendFlightCancellationNotification(event.getFlight(), event.getAffectedBookings());
    }
}
