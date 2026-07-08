package com.hotak.noonchibot.connector.web;

public class WebSocketSubscriptionFailedException extends WebSocketErrorMessageReceivedException {
    public WebSocketSubscriptionFailedException(String message) {
        super(message);
    }
}
