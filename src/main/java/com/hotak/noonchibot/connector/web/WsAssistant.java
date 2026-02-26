package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class WsAssistant {
    private final WebSocketClient webSocketClient;
    private final WebSocketHttpHeaders headers;
    private final List<WsPreProcessor> preProcessors;
    private final List<WsPostProcessor> postProcessors;
    private final ObjectMapper objectMapper;
    private final Authenticator authenticator;
    private volatile WsConnection wsConnection;

    public WsConnection connect(URI wsUrl) {
        if(isConnected()) return wsConnection;
        wsConnection = new WsConnection(objectMapper, authenticator, preProcessors, postProcessors);
        webSocketClient.execute(wsConnection, headers, wsUrl).join();
        return wsConnection;
    }

    public void send(WsRequest request) {
        if(wsConnection == null) throw new WebSocketNotConnectedException("not yet connected, call connect method first");
        wsConnection.send(request);
    }

    public boolean isConnected() {
        return wsConnection != null && wsConnection.isConnected();
    }

    public void disconnect() {
        if(wsConnection == null) return;
        wsConnection.disconnect();
    }
}