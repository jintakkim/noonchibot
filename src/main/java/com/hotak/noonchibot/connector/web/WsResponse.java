package com.hotak.noonchibot.connector.web;

public record WsResponse(String data, MessageType messageType) {
    public enum MessageType {
        TEXT, BINARY
    }
}
