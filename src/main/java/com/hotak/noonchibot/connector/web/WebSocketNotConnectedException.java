package com.hotak.noonchibot.connector.web;

public class WebSocketNotConnectedException extends RuntimeException {
    public WebSocketNotConnectedException(String message) {
        super(message);
    }
}
