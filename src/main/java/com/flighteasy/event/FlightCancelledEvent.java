package com.flighteasy.event;

import com.flighteasy.entity.Flight;

public class FlightCancelledEvent {
    private final Flight flight;
    public FlightCancelledEvent(Flight flight){
        this.flight = flight;
    }

    public Flight getFlight() {
        return flight;
    }
}
