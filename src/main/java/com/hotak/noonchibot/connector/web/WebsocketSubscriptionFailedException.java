package com.hotak.noonchibot.connector.web;

public class WebsocketSubscriptionFailedException extends RuntimeException {
    public WebsocketSubscriptionFailedException(String message) {
        super(message);
    }
}
