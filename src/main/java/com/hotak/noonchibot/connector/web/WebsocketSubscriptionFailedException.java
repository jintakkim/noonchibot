package com.hotak.noonchibot.connector.web;

public class WebsocketSubscriptionFailedException extends WebsocketErrorMessageReceivedException {
    public WebsocketSubscriptionFailedException(String message) {
        super(message);
    }
}
