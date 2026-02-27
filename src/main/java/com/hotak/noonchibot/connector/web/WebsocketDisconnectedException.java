package com.hotak.noonchibot.connector.web;

public class WebsocketDisconnectedException extends RuntimeException {
    public WebsocketDisconnectedException(String message) {
        super(message);
    }
}
