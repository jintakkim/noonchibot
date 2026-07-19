package com.hotak.noonchibot.core;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.runtime.ExchangeRuntimeFailureReporter;
import com.hotak.noonchibot.core.runtime.ExchangeRuntimeFailure;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractWebsocketDataSource implements SmartLifecycle, WebsocketStatus, WsConnectionListener {
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final WsAssistant wsAssistant;
    protected final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final ExchangeRuntimeFailureReporter failureReporter;
    private volatile boolean running;
    private int consecutiveFailures;
    private volatile ScheduledFuture<?> reconnectTask;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile Instant lastRecvTime;
    protected volatile WsConnection wsConnection;

    @Override
    public final void onConnected(WsConnection connection) {
        if (!running) {
            connection.disconnect(CloseStatus.NORMAL);
            return;
        }
        wsConnection = connection;
        handleConnected();
        startHeartbeat(connection);
    }

    @Override
    public final void onMessage(WsResponse response) {
        lastRecvTime = Instant.now();
        handleResult(processMessage(response));

    }

    @Override
    public final void onError(Throwable error) {
        log.warn("WebSocket transport error: source={}, endpoint={}; waiting for close: {}",
                getClass().getName(), endpoint(), error.getMessage());
    }

    @Override
    public final void onClosed(CloseStatus status) {
        stopHeartbeat();
        wsConnection = null;
        scheduleReconnect(status);
    }

    private void handleResult(WebsocketMessageResult result) {
        switch (result.directive()) {
            case ACKNOWLEDGED -> { }
            case IGNORED -> log.warn("Ignored WebSocket message: {}", result.reason());
            case MESSAGE_PROCESSED -> resetFailures();
            case RECONNECT -> wsConnection.disconnect(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * back off reconnect
     */
    private void scheduleReconnect(CloseStatus status) {
        if (!running) return;
        int failureCount = ++consecutiveFailures;
        if (failureCount >= MAX_CONSECUTIVE_FAILURES) {
            stop();
            log.error("WebSocket stopped: source={}, endpoint={}, consecutiveFailures={}",
                    getClass().getName(), endpoint(), failureCount);
            failureReporter.report(new ExchangeRuntimeFailure(
                    exchange,
                    getClass().getName(),
                    new WebsocketUnavailableException(
                            endpoint(),
                            status,
                            failureCount
                    ),
                    Instant.now()
            ));
            return;
        }
        Duration delay = INITIAL_RETRY_DELAY.multipliedBy(1L << (failureCount - 1));
        if (delay.compareTo(MAX_RETRY_DELAY) > 0) delay = MAX_RETRY_DELAY;
        log.warn("WebSocket closed: source={}, endpoint={}, status={}; reconnecting ({}/{}) in {}s",
                getClass().getName(), endpoint(), status,
                failureCount, MAX_CONSECUTIVE_FAILURES, delay.toSeconds());
        reconnectTask = taskScheduler.schedule(this::reconnect, Instant.now().plus(delay));
    }

    private void reconnect() {
        reconnectTask = null;
        if (running) wsAssistant.connect(connectionUri(), this);
    }

    private void resetFailures() {
        consecutiveFailures = 0;
    }

    private void startHeartbeat(WsConnection connection) {
        Duration interval = heartbeatInterval();
        if (interval == null) return;
        stopHeartbeat();
        heartbeatTask = taskScheduler.scheduleAtFixedRate(
                () -> {
                    if (connection == wsConnection && connection.isConnected()) {
                        sendHeartbeat(connection);
                    }
                },
                Instant.now().plus(interval),
                interval
        );
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        heartbeatTask = null;
        if (task != null) task.cancel(false);
    }

    private String endpoint() {
        URI uri = connectionUri();
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    protected abstract URI connectionUri();
    protected Duration heartbeatInterval() { return null; }
    protected void sendHeartbeat(WsConnection connection) { }
    protected abstract void handleConnected();
    protected abstract WebsocketMessageResult processMessage(WsResponse wsResponse);

    @Override
    public final Instant getLastRecvTime() {
        return lastRecvTime;
    }

    @Override
    public boolean isConnected() {
        WsConnection connection = wsConnection;
        return connection != null && connection.isConnected();
    }

    @Override
    public final void start() {
        if (running) {
            return;
        }
        consecutiveFailures = 0;
        running = true;
        doStart();
    }

    protected void doStart() {
        wsAssistant.connect(connectionUri(), this);
    }

    @Override
    public final void stop() {
        if (!running) {
            return;
        }
        running = false;
        doStop();
    }

    protected void doStop() {
        stopHeartbeat();
        ScheduledFuture<?> task = reconnectTask;
        reconnectTask = null;
        if (task != null) task.cancel(false);
        WsConnection connection = wsConnection;
        wsConnection = null;
        if (connection != null) connection.disconnect(CloseStatus.NORMAL);
    }

    @Override
    public final boolean isRunning() {
        return running;
    }
}
