package com.hotak.noonchibot.connector.web;

import org.springframework.web.socket.CloseStatus;

public interface WsConnection {
    void send(WsRequest request);
    void disconnect(CloseStatus status);
    boolean isConnected();
}
