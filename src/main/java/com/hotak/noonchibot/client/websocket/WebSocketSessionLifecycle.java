package com.hotak.noonchibot.client.websocket;

public interface WebSocketSessionLifecycle {
    void sessionClosed(String sessionId);
}
