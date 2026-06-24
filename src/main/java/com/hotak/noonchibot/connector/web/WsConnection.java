package com.hotak.noonchibot.connector.web;

public interface WsConnection {
    void send(WsRequest request);
    void disconnect();
    boolean isConnected();
    WsResponse take() throws InterruptedException;
}
