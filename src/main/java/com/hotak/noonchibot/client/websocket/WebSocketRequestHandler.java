package com.hotak.noonchibot.client.websocket;

import tools.jackson.databind.JsonNode;

public interface WebSocketRequestHandler {
    String method();

    String subscriptionType();

    void handle(String sessionId, JsonNode subscription);
}
