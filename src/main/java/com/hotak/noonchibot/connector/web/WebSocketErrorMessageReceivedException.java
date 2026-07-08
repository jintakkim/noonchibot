package com.hotak.noonchibot.connector.web;

public class WebSocketErrorMessageReceivedException extends RuntimeException {
    public WebSocketErrorMessageReceivedException(String message) {
        super(message);
    }
}
