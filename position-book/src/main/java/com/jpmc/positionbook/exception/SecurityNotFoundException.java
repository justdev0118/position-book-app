package com.jpmc.positionbook.exception;

public class SecurityNotFoundException extends RuntimeException {
    public SecurityNotFoundException(String message) {
        super(message);
    }
}
