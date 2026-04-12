package com.fighteasy.exception.custom;

public class InvalidStatusTransittionException extends RuntimeException {
    public InvalidStatusTransittionException(String message) {
        super(message);
    }
}
