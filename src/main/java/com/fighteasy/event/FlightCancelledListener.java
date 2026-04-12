package com.fighteasy.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightCancelledListener {

    @EventListener
    public void onFlightCancelled(FlightCancelledEvent event){
        log.info("Flight {} cancelled - processing affeccted booking...",
                event.getFlight().getFlightNumber());
    }
}
