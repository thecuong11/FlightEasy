package com.flighteasy.exception.custom;

public class DuplicatePassengerException extends RuntimeException {
    public DuplicatePassengerException(String message) {
        super(message);
    }
}
