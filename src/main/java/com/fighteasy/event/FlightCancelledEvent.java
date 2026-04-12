package com.fighteasy.event;

import com.fighteasy.entity.Flight;

public class FlightCancelledEvent {
    private final Flight flight;
    public FlightCancelledEvent(Flight flight){
        this.flight = flight;
    }

    public Flight getFlight() {
        return flight;
    }
}
