package com.hotak.noonchibot.connector.web;

import org.springframework.web.socket.CloseStatus;

public interface WsConnectionListener {
    void onConnected(WsConnection connection);
    void onMessage(WsResponse response);
    void onError(Throwable error);
    void onClosed(CloseStatus status);
}
