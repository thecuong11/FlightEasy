package com.flighteasy.exception.custom;

public class EmailAlreadyExistsException extends RuntimeException{
    public EmailAlreadyExistsException(String msg) {super(msg);}
}
