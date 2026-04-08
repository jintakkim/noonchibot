package com.hotak.noonchibot.connector.binance;

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
public class BinanceUserStreamEventPublisher implements WebsocketStatus, SmartLifecycle {
    private final WsAssistant wsAssistant;
    private final ObjectMapper objectMapper;
    private final BinanceAuthenticator binanceAuthenticator;
    private final List<UserStreamEventParser> userStreamEventParsers;
    private final ExchangeEventPublisher exchangeEventPublisher;
    private final IoExecutor ioExecutor;

    private volatile Instant lastRecvTime;
    private volatile WsConnection wsConnection;
    private volatile boolean running = false;
    private volatile Future<?> connection;

    private void connectionLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                this.wsConnection = wsAssistant.connect(URI.create(BinanceApiSpec.WSS_API_URL));
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
        Map<String, Object> params = binanceAuthenticator.generateWsSubscribeParams();
        String requestId = UUID.randomUUID().toString();

        wsConnection.send(new WsRequest(Map.of(
                "id", requestId,
                "method", "userDataStream.subscribe.signature",
                "params", params
        ), false));

        // 구독 응답 확인
        WsResponse response = wsConnection.take();
        JsonNode data = objectMapper.readTree(response.data());
        if (data.path("status").asInt() != 200) {
            throw new WebsocketSubscriptionFailedException("Error subscribing to user stream: " + data);
        }
    }

    private void processMessage() throws InterruptedException {
        WsResponse response = wsConnection.take();
        lastRecvTime = Instant.now();
        JsonNode msg = objectMapper.readTree(response.data());
        // 구독 확인 응답 무시
        if (msg.has("id") && msg.has("status")) return;
        // WS API 이벤트 컨테이너 언래핑
        if (msg.has("event") && msg.has("subscriptionId")) {
            msg = msg.get("event");
        }
        // 스트림 종료
        if ("eventStreamTerminated".equals(msg.path("e").asString())) {
            throw new WebsocketDisconnectedException("Stream terminated by server");
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
        running = true;
    }

    @Override
    public void stop() {
        connection.cancel(true);
        connection = null;
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
