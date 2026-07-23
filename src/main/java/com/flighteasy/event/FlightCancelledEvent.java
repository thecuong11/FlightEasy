package com.flighteasy.event;

import com.flighteasy.entity.Booking;
import com.flighteasy.entity.Flight;

import java.util.List;

public class FlightCancelledEvent {
    private final Flight flight;
    private final List<Booking> affectedBookings;
    public FlightCancelledEvent(Flight flight, List<Booking> affectedBookings){
        this.flight = flight;
        this.affectedBookings = affectedBookings;
    }

    public Flight getFlight() {
        return flight;
    }

    public List<Booking> getAffectedBookings() {
        return affectedBookings;
    }
}
