package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;

import java.util.List;

public class WsServer {
    private final MockWsConnection connection;

    public WsServer(MockWsConnection connection) {
        this.connection = connection;
    }

    public void push(WsResponse response) {
        connection.push(response);
    }

    public List<WsRequest> receivedRequests() {
        return connection.sentRequests;
    }
}
