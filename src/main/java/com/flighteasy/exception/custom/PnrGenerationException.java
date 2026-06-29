package com.flighteasy.exception.custom;

public class PnrGenerationException extends RuntimeException {
    public PnrGenerationException(String message) {
        super(message);
    }
}
