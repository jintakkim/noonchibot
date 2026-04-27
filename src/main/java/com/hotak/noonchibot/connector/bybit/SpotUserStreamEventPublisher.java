package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
class SpotUserStreamEventPublisher implements WebsocketStatus, LifecycleComponent {
    private final WsAssistant wsAssistant;
    private final ObjectMapper objectMapper;
    private final BybitAuthenticator bybitAuthenticator;
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

    private void subscribeUserStream() throws InterruptedException {
        wsConnection.send(new WsRequest(bybitAuthenticator.generateWsAuthParams(), false));

        WsResponse authResponse = wsConnection.take();
        JsonNode authData = objectMapper.readTree(authResponse.data());
        if (!authData.path("success").asBoolean()) {
            throw new WebsocketSubscriptionFailedException("Bybit WS auth failed: " + authData);
        }

        wsConnection.send(new WsRequest(Map.of(
                "op", "subscribe",
                "args", List.of("execution", "order", "wallet")
        ), false));

        WsResponse subResponse = wsConnection.take();
        JsonNode subData = objectMapper.readTree(subResponse.data());
        if (!subData.path("success").asBoolean()) {
            throw new WebsocketSubscriptionFailedException("Bybit WS subscribe failed: " + subData);
        }
    }

    private void processMessage() throws InterruptedException {
        WsResponse response = wsConnection.take();
        lastRecvTime = Instant.now();
        JsonNode msg = objectMapper.readTree(response.data());

        // op 응답(auth/subscribe/pong ack 등) 무시 — 실제 데이터 메시지는 topic 필드를 가진다
        if (!msg.has("topic")) return;

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
