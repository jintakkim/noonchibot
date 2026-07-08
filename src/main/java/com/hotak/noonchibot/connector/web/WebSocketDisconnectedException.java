package com.hotak.noonchibot.connector.web;

public class WebSocketDisconnectedException extends RuntimeException {
    public WebSocketDisconnectedException(String message) {
        super(message);
    }
}
