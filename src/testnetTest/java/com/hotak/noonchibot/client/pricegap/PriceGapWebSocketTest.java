package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.ClientWebSocketConfig;
import com.hotak.noonchibot.client.websocket.ClientWebSocketHandler;
import com.hotak.noonchibot.client.websocket.WebSocketRequestDispatcher;
import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.price.LastTradePrice;
import com.hotak.noonchibot.core.price.StableQuotePriceConverter;
import com.hotak.noonchibot.core.pricegap.PriceGapFeedDefinition;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotProducer;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotPublisher;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotStore;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.LAST_TRADE_PRICE_GAP_EVENT;

@SpringBootTest(
        classes = PriceGapWebSocketTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class PriceGapWebSocketTest {
    private static final PriceGapSubscriptionKey KEY = new PriceGapSubscriptionKey("BTC-USDT");

    @LocalServerPort
    private int port;

    @Autowired
    private PriceGapSubscriptionRegistry registry;

    @Autowired
    private PriceGapSnapshotStore store;

    @Autowired
    private PriceGapSnapshotPublisher snapshotPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void subscribe_receivesNormalizedPriceGapSnapshot() throws Exception {
        CapturingHandler clientHandler = new CapturingHandler();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(clientHandler, "ws://localhost:" + port + ClientWebSocketConfig.ENDPOINT)
                .get(10, TimeUnit.SECONDS);

        try {
            session.sendMessage(new TextMessage("""
                    {
                      "method": "subscribe",
                      "subscription": {
                        "type": "priceGap",
                        "pairs": ["BTC-USDT"]
                      }
                    }
                    """));
            awaitSubscription(registry);

            Instant timestamp = Instant.now();
            PriceGapSnapshotProducer producer = producer(registry, store, timestamp);
            producer.onTick(timestamp);

            assertThat(clientHandler.messageReceived.await(10, TimeUnit.SECONDS)).isTrue();
            JsonNode message = objectMapper.readTree(clientHandler.payload.get());
            assertThat(message.path("type").asString()).isEqualTo(LAST_TRADE_PRICE_GAP_EVENT);
            assertThat(message.path("data").get(0).path("key").path("tradingPair").asText())
                    .isEqualTo("BTC-USDT");
            assertThat(message.path("data").get(0).path("exchanges").size()).isEqualTo(2);
            assertThat(message.path("data").get(0).path("spread").isObject()).isTrue();
            assertThat(store.latest(KEY)).isPresent();
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }

    private PriceGapSnapshotProducer producer(
            PriceGapSubscriptionRegistry registry,
            PriceGapSnapshotStore store,
            Instant timestamp
    ) {
        OrderBookTracker binanceSpot = tracker("USDC-USDT", "1.001", timestamp);
        OrderBookTracker binanceDerivative = tracker("BTC-USDT", "60000", timestamp);
        OrderBookTracker hyperliquid = tracker("BTC-USDC", "59900", timestamp);
        return new PriceGapSnapshotProducer(
                registry,
                store,
                snapshotPublisher,
                java.util.List.of(new PriceGapFeedDefinition(
                        KEY,
                        Map.of(
                                Exchange.BINANCE_DERIVATIVE, "BTC-USDT",
                                Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC"
                        ),
                        Exchange.BINANCE_SPOT,
                        "USDC-USDT"
                )),
                Map.of(
                        Exchange.BINANCE_SPOT, binanceSpot,
                        Exchange.BINANCE_DERIVATIVE, binanceDerivative,
                        Exchange.HYPERLIQUID_DERIVATIVE, hyperliquid
                ),
                Map.of(),
                new StableQuotePriceConverter()
        );
    }

    private OrderBookTracker tracker(String tradingPair, String price, Instant timestamp) {
        OrderBookTracker tracker = mock(OrderBookTracker.class);
        when(tracker.findLastTradePrice(tradingPair)).thenReturn(Optional.of(
                new LastTradePrice(new BigDecimal(price), timestamp)
        ));
        return tracker;
    }

    private void awaitSubscription(PriceGapSubscriptionRegistry registry) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!registry.activeKeys().contains(KEY) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(registry.activeKeys()).contains(KEY);
    }

    private static final class CapturingHandler extends TextWebSocketHandler {
        private final CountDownLatch messageReceived = new CountDownLatch(1);
        private final AtomicReference<String> payload = new AtomicReference<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            payload.set(message.getPayload());
            messageReceived.countDown();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({
            ClientWebSocketConfig.class,
            ClientWebSocketHandler.class,
            WebSocketSessions.class,
            WebSocketRequestDispatcher.class,
            PriceGapClientConfig.class,
            PriceGapSessionLifecycle.class,
            SubscribePriceGapRequestHandler.class,
            UnsubscribePriceGapRequestHandler.class
    })
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
