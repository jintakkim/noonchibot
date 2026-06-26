package com.hotak.noonchibot.connector;

public class NotRegisteredException extends IllegalArgumentException {
    public NotRegisteredException(String message) {
        super(message);
    }
}
