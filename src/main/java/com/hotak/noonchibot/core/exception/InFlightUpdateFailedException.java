package com.hotak.noonchibot.core.exception;

public class InFlightUpdateFailedException extends RuntimeException {
    public InFlightUpdateFailedException(String message) {
        super(message);
    }
}
