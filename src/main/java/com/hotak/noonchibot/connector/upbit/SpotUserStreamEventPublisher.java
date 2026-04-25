package com.hotak.noonchibot.connector.upbit;

import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
public class SpotUserStreamEventPublisher implements WebsocketStatus, LifecycleComponent {
    private final WsAssistant wsAssistant;
    private final ObjectMapper objectMapper;
    private final UpbitAuthenticator upbitAuthenticator;
    private final List<UserStreamEventParser> userStreamEventParsers;
    private final ExchangeEventPublisher exchangeEventPublisher;
    private final IoExecutor ioExecutor;

    private volatile Instant lastRecvTime;
    private volatile WsConnection wsConnection;
    private volatile Future<?> connection;

    private void connectionLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                this.wsConnection = wsAssistant.connect(URI.create(SpotApiSpec.WSS_API_URL));
                subscribeUserStream();
                while (true) {
                    processMessage();
                }
            } catch (WebsocketDisconnectedException e) {
                log.warn("User stream disconnected, reconnecting in 1s");
                try {
                    sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Unexpected error in user stream, reconnecting in 5s", e);
                try {
                    sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                wsConnection.disconnect();
                wsConnection = null;
            }
        }
    }

    private void subscribeUserStream() {
        String ticket = UUID.randomUUID().toString();

        List<Map<String, Object>> subscribeMessage = List.of(
                Map.of("ticket", ticket),
                Map.of("type", "myOrder"),
                Map.of("type", "myAsset"),
                Map.of("format", "DEFAULT")
        );

        wsConnection.send(new WsRequest(subscribeMessage, false));
    }

    private void processMessage() throws InterruptedException {
        WsResponse response = wsConnection.take();
        lastRecvTime = Instant.now();
        JsonNode msg = objectMapper.readTree(response.data());

        if (msg.has("error")) {
            JsonNode error = msg.path("error");
            String errorName = error.path("name").asString();
            String errorMessage = error.path("message").asString();
            log.error("업비트 WS 에러: {} - {}", errorName, errorMessage);

            if ("INVALID_AUTH".equals(errorName)) {
                throw new WebsocketDisconnectedException("Auth failed: " + errorMessage);
            }
            return;
        }

        for (UserStreamEventParser parser : userStreamEventParsers) {
            if (parser.canParse(msg)) {
                parser.parse(msg).forEach(exchangeEventPublisher::publish);
                return;
            }
        }
    }

    @Override
    public boolean isConnected() {
        return wsConnection != null && wsConnection.isConnected();
    }

    @Override
    public Instant getLastRecvTime() {
        return lastRecvTime;
    }

    @Override
    public void start() {
        connection = ioExecutor.submit(this::connectionLoop);
    }

    @Override
    public void shutdown() {
        connection.cancel(true);
        connection = null;
    }
}

