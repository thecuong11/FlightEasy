package com.flighteasy.exception.custom;

public class InvalidStatusTransittionException extends RuntimeException {
    public InvalidStatusTransittionException(String message) {
        super(message);
    }
}
