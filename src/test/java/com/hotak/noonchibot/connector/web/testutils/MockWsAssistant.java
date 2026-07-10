package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsConnection;
import com.hotak.noonchibot.connector.web.WsConnectionListener;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class MockWsAssistant implements WsAssistant {
    private final Map<String, MockWsConnection> mockWsConnection = new HashMap<>();

    public MockWsConnection addMockConnection(String wsUrl) {
        MockWsConnection connection = new MockWsConnection(URI.create(wsUrl));
        mockWsConnection.put(wsUrl, connection);
        return connection;
    }

    public void removeMockConnection(String wsUrl) {
        mockWsConnection.remove(wsUrl);
    }

    @Override
    public WsConnection connect(URI wsUrl, WsConnectionListener listener) {
        MockWsConnection connection = mockWsConnection.get(wsUrl.toString());
        if(connection == null) {
            throw new IllegalStateException("no mock connection found for " + wsUrl);
        }
        connection.connect(listener);
        return connection;
    }
}
