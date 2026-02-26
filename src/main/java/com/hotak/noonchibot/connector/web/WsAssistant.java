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
    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final WebSocketClient webSocketClient;
    private final WebSocketHttpHeaders headers;
    private final List<WsPreProcessor> preProcessors;
    private final List<WsPostProcessor> postProcessors;
    private final ObjectMapper objectMapper;
    private final Authenticator authenticator;
    private final TaskScheduler taskScheduler;
    private volatile WsConnection wsConnection;
    private volatile boolean isIntentionalClosed = false;
    private final AtomicInteger retryAttempts = new AtomicInteger(0);

    public void connect(URI wsUrl) {
        if(isConnected()) return;
        wsConnection = new WsConnection(objectMapper, authenticator, preProcessors, postProcessors);
        webSocketClient.execute(wsConnection, headers, wsUrl).join();
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
        isIntentionalClosed = true;
        wsConnection.disconnect();
    }

    private void scheduleReconnect(URI wsUrl) {
        int attempt = retryAttempts.incrementAndGet();
        if (attempt > MAX_RETRY_ATTEMPTS) {
            log.error("Max reconnect attempts reached");
            return;
        }
        long backoff = Math.min(INITIAL_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
        log.info("Reconnecting in {}ms (attempt {}/{})", backoff, attempt, MAX_RETRY_ATTEMPTS);

        taskScheduler.schedule(() -> {
            try {
                connect(wsUrl);
                resubscribe();
            } catch (Exception e) {
                log.error("Reconnect failed", e);
                scheduleReconnect(wsUrl);
            }
        }, Instant.now().plusMillis(backoff));
    }
}