package com.hotak.noonchibot.core;

import com.hotak.noonchibot.connector.web.WsConnection;

public final class AbstractWebsocketDataSourceTestUtils {
    public static void setWsConnection(AbstractWebsocketDataSource websocketDataSource, WsConnection wsConnection) {
        websocketDataSource.wsConnection = wsConnection;
    }
}
