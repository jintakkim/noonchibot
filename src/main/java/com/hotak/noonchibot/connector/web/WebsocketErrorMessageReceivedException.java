package com.hotak.noonchibot.connector.web;

public class WebsocketErrorMessageReceivedException extends RuntimeException {
    public WebsocketErrorMessageReceivedException(String message) {
        super(message);
    }
}
