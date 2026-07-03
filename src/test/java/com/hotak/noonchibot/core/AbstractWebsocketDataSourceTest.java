package com.hotak.noonchibot.core;

import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.connector.web.testutils.WsClientTest;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractWebsocketDataSourceTest<T extends AbstractWebsocketDataSource> extends WsClientTest {
    protected T dataSource;

    @BeforeEach
    void setup() {
        dataSource = createWebsocketDataSource(wsAssistant);
    }

    protected abstract T createWebsocketDataSource(MockWsAssistant wsAssistant);

    public void doConnection() {
        dataSource.onStart();
    }
}
