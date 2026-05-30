package com.flighteasy.event;

import com.flighteasy.entity.Booking;

public class BookingCancelledEvent {
    private final Booking booking;

    public BookingCancelledEvent(Booking booking) {
        this.booking = booking;
    }

    public Booking getBooking() {return booking;}
}
