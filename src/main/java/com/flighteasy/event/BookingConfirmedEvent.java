package com.flighteasy.event;

import com.flighteasy.entity.Booking;

public class BookingConfirmedEvent {
    private final Booking booking;

    public BookingConfirmedEvent(Booking booking) {
        this.booking = booking;
    }

    public Booking getBooking() {
        return booking;
    }
}
