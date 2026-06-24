package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.WsAssistant;

import java.util.List;
import java.util.function.Consumer;

public class ClientTest {
    private final RestClientTest restClientTest;
    private final WsClientTest wsClientTest;

    public RestAssistant getRestAssistant() {
        return restClientTest.restAssistant;
    }

    public WsAssistant getWsAssistant() {
        return wsClientTest.wsAssistant;
    }


    public ClientTest() {
        this.restClientTest = new RestClientTest();
        this.wsClientTest = new WsClientTest();
    }

    public void runWith(List<RestFixture> restFixtures, Runnable runnable) {
        restClientTest.runWith(restFixtures, runnable);
    }

    public void runWith(RestFixture restFixture, Runnable runnable) {
        restClientTest.runWith(restFixture, runnable);
    }

    public void runWith(String wsUri, Consumer<WsServer> testable) {
        wsClientTest.runWith(wsUri, testable);
    }
}
