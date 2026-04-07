package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.TestMainExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.event.OrderLostEvent;
import com.hotak.noonchibot.core.event.TestExchangeEventPublisher;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BinanceOrderStatusPollerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private RestAssistant restAssistant;
    private OrderTracker orderTracker;
    private TradingPairSymbolRegistry symbolRegistry;
    private BinanceOrderStatusPoller poller;
    private TestExchangeEventPublisher testExchangeEventPublisher;

    @BeforeEach
    void setUp() {
        restAssistant = Mockito.mock(RestAssistant.class);
        orderTracker = Mockito.mock(OrderTracker.class);
        testExchangeEventPublisher = new TestExchangeEventPublisher();
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT"));
        poller = new BinanceOrderStatusPoller(
                restAssistant,
                testExchangeEventPublisher,
                orderTracker,
                new TestMainExecutor(),
                Runnable::run,
                symbolRegistry,
                Mockito.mock(WebsocketStatus.class),
                mock(TaskScheduler.class)
        );
    }

    private InFlightOrder createActiveOrder(String clientOrderId, String exchangeOrderId) {
        return new InFlightOrder(
                clientOrderId, "BTC-USDT", OrderType.LIMIT,
                TradeType.BUY, new BigDecimal("0.01"), new BigDecimal("50000"),
                Instant.now(), exchangeOrderId, false, TimeInForce.GTC, new HashSet<>(), new HashMap<>()
        );
    }

    @Test
    @DisplayName("활성 주문의 상태가 업데이트된다")
    void updatesOrderStatus() {
        InFlightOrder order = createActiveOrder("BUY-BTC-USDT-1", "12345");
        when(orderTracker.getAll()).thenReturn(List.of(order));
        stubOrderStatusResponse("12345", "PARTIALLY_FILLED");

        poller.pollData();

        assertThat(testExchangeEventPublisher.getPublishedEvents())
                .hasSize(1)
                .first()
                .isInstanceOfSatisfying(OrderUpdateEvent.class, event -> {
                    assertThat(event.newState()).isEqualTo(OrderState.PARTIALLY_FILLED);
                    assertThat(event.exchangeOrderId()).isEqualTo("12345");
                });
    }

    @Test
    @DisplayName("거래소에서 주문을 찾지 못하면 OrderLost 이벤트가 발생한다")
    void orderNotFound() {
        InFlightOrder order = createActiveOrder("BUY-BTC-USDT-1", "12345");
        when(orderTracker.getAll()).thenReturn(List.of(order));
        when(restAssistant.executeRequestAndGetJsonBody(any()))
                .thenThrow(new ExchangeApiException(
                        HttpStatusCode.valueOf(400),
                        "{\"code\":-2013,\"msg\":\"Order does not exist.\"}"));

        poller.pollData();

        assertThat(testExchangeEventPublisher.getPublishedEvents())
                .hasSize(1)
                .first()
                .isInstanceOfSatisfying(OrderLostEvent.class, event ->
                        assertThat(event.clientOrderId()).isEqualTo("BUY-BTC-USDT-1"));
    }

    @Test
    @DisplayName("활성 주문이 없으면 API를 호출하지 않는다")
    void noActiveOrders() {
        when(orderTracker.getAll()).thenReturn(List.of());

        poller.pollData();

        verify(restAssistant, never()).executeRequestAndGetJsonBody(any());
    }

    @Test
    @DisplayName("여러 주문의 상태가 각각 업데이트된다")
    void updatesMultipleOrders() {
        InFlightOrder order1 = createActiveOrder("ORDER-1", "111");
        InFlightOrder order2 = createActiveOrder("ORDER-2", "222");
        when(orderTracker.getAll()).thenReturn(List.of(order1, order2));

        when(restAssistant.executeRequestAndGetJsonBody(any())).thenAnswer(inv -> {
            RestRequest req = inv.getArgument(0);
            String clientOrderId = (String) req.params().get("origClientOrderId");
            return switch (clientOrderId) {
                case "ORDER-1" -> createOrderStatusNode("111", "FILLED");
                case "ORDER-2" -> createOrderStatusNode("222", "CANCELED");
                default -> throw new IllegalArgumentException("unexpected: " + clientOrderId);
            };
        });

        poller.pollData();

        assertThat(testExchangeEventPublisher.getEventsOfType(OrderUpdateEvent.class))
                .hasSize(2)
                .extracting(OrderUpdateEvent::exchangeOrderId)
                .containsExactlyInAnyOrder("111", "222");
    }

    private void stubOrderStatusResponse(String exchangeOrderId, String status) {
        when(restAssistant.executeRequestAndGetJsonBody(any()))
                .thenReturn(createOrderStatusNode(exchangeOrderId, status));
    }

    private ObjectNode createOrderStatusNode(String exchangeOrderId, String status) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("orderId", exchangeOrderId);
        node.put("status", status);
        node.put("updateTime", Instant.now().toEpochMilli());
        return node;
    }

}