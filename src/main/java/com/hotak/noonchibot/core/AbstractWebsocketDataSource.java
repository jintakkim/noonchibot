package com.hotak.noonchibot.core;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractWebsocketDataSource implements LifecycleAware, WebsocketStatus {
    private final WsAssistant wsAssistant;
    protected final ObjectMapper objectMapper;
    private final IoExecutor ioExecutor;
    private volatile Instant lastRecvTime;
    private volatile Future<?> connectionFuture;
    protected volatile WsConnection wsConnection;

    private void connectionLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                doConnection();
                while (true) {
                    WsResponse res = wsConnection.take();
                    lastRecvTime = Instant.now();
                    try {
                        processMessage(res);
                    } catch (WebSocketErrorMessageReceivedException e) {
                        if(e instanceof WebSocketSubscriptionFailedException) {
                            log.error("subscription failed", e);
                            return;
                        }
                        log.error("server-side error message received: {}", e.getMessage());
                    }
                }
            } catch (WebSocketDisconnectedException e) {
                log.warn("disconnected, reconnecting in 1s");
                try {
                    Thread.sleep(Duration.ofSeconds(1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("unexpected error", e);
            } finally {
                if (wsConnection != null) wsConnection.disconnect();
                wsConnection = null;
            }
        }
    }

    @VisibleForTesting
    void doConnection() {
        wsConnection = wsAssistant.connect(connectionUri());
        onConnected();
    }

    protected abstract URI connectionUri();

    @Override
    public Instant getLastRecvTime() {
        return lastRecvTime;
    }

    @Override
    public boolean isConnected() {
        return wsConnection != null && wsConnection.isConnected();
    }

    protected abstract void onConnected();
    protected abstract void processMessage(WsResponse wsResponse);

    @Override
    public void onStart() {
        connectionFuture = ioExecutor.submit(this::connectionLoop);
    }

    @Override
    public void onShutdown() {
        if (connectionFuture != null) {
            connectionFuture.cancel(true);
            connectionFuture = null;
        }
    }
}
