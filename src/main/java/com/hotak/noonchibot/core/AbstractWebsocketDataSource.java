package com.hotak.noonchibot.core;

import com.hotak.noonchibot.connector.web.WebsocketDisconnectedException;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.AsyncTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Future;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractWebsocketDataSource implements SmartLifecycle {
    private final WsAssistant wsAssistant;
    private final String wsUrl;
    protected final ObjectMapper objectMapper;
    private final AsyncTaskExecutor taskExecutor;

    private volatile Future<?> connectionFuture;
    private volatile boolean running = false;
    protected volatile WsConnection wsConnection;

    private void connectionLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                wsConnection = wsAssistant.connect(URI.create(wsUrl));
                onConnected();
                while (true) {
                    processMessage();
                }
            } catch (WebsocketDisconnectedException e) {
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

    protected abstract void onConnected();
    protected abstract void processMessage() throws InterruptedException;

    @Override
    public void start() {
        running = true;
        connectionFuture = taskExecutor.submit(this::connectionLoop);
    }

    @Override
    public void stop() {
        running = false;
        if (connectionFuture != null) connectionFuture.cancel(true);
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
