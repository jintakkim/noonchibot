package com.hotak.noonchibot.connector.web.testutils;

import java.util.function.Consumer;

public class WsClientTest {
    public final MockWsAssistant wsAssistant = new MockWsAssistant();

    public void runWith(String wsUri, Consumer<WsServer> testable) {
        try {
            MockWsConnection connection = wsAssistant.addMockConnection(wsUri);
            testable.accept(connection.getScenario());
        } finally {
            wsAssistant.removeMockConnection(wsUri);
        }
    }
}