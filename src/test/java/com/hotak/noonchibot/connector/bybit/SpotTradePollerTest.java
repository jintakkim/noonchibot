package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.TestMainExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.event.TestExchangeEventPublisher;
import com.hotak.noonchibot.core.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpotTradePollerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private RestAssistant restAssistant;
    private OrderTracker orderTracker;
    private TradingPairSymbolRegistry symbolRegistry;
    private SpotTradePoller poller;
    private TestExchangeEventPublisher testExchangeEventPublisher;

    @BeforeEach
    void setUp() {
        restAssistant = Mockito.mock(RestAssistant.class);
        testExchangeEventPublisher = new TestExchangeEventPublisher();
        orderTracker = Mockito.mock(OrderTracker.class);
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT"));
        poller = new SpotTradePoller(
                Mockito.mock(WebsocketStatus.class),
                testExchangeEventPublisher,
                restAssistant,
                mock(TaskScheduler.class),
                orderTracker,
                symbolRegistry,
                Runnable::run,
                new TestMainExecutor(),
                SpotApiSpec.MY_TRADES_PATH_URL
        );
    }

    private InFlightOrder createInFlightOrder(String clientOrderId, String exchangeOrderId) {
        return new InFlightOrder(
                clientOrderId, "BTC-USDT", OrderType.LIMIT,
                TradeType.BUY, new BigDecimal("0.01"), new BigDecimal("50000"),
                Instant.now(), exchangeOrderId, false, TimeInForce.GTC, new HashSet<>(), new HashMap<>()
        );
    }

    @Test
    @DisplayName("체결 내역이 TradeUpdateEvent로 발행된다")
    void processesTradeUpdates() {
        InFlightOrder order = createInFlightOrder("BUY-BTC-USDT-1", "12345");
        when(orderTracker.getAll()).thenReturn(List.of(order));
        stubTradesResponse(List.of(
                createTradeNode("t1", "12345", "50000", "0.005", "0.25", "USDT", true)
        ));

        poller.pollData();
        assertThat(testExchangeEventPublisher.getEventsOfType(TradeUpdateEvent.class))
                .hasSize(1)
                .first()
                .satisfies(trade -> {
                    assertThat(trade.tradeId()).isEqualTo("t1");
                    assertThat(trade.fillPrice()).isEqualByComparingTo(new BigDecimal("50000"));
                    assertThat(trade.fillBaseAmount()).isEqualByComparingTo(new BigDecimal("0.005"));
                    assertThat(trade.isMaker()).isTrue();
                });
    }

    @Test
    @DisplayName("여러 체결 내역이 모두 발행된다")
    void processesMultipleTradeUpdates() {
        InFlightOrder order = createInFlightOrder("BUY-BTC-USDT-1", "12345");
        when(orderTracker.getAll()).thenReturn(List.of(order));
        stubTradesResponse(List.of(
                createTradeNode("t1", "12345", "50000", "0.003", "0.15", "USDT", true),
                createTradeNode("t2", "12345", "50100", "0.007", "0.35", "USDT", false)
        ));

        poller.pollData();
        assertThat(testExchangeEventPublisher.getEventsOfType(TradeUpdateEvent.class)).hasSize(2);
    }

    @Test
    @DisplayName("exchangeOrderId가 UNKNOWN이면 조회하지 않는다")
    void skipsUnknownExchangeOrderId() {
        InFlightOrder order = createInFlightOrder("BUY-BTC-USDT-1", "UNKNOWN");
        when(orderTracker.getAll()).thenReturn(List.of(order));
        poller.pollData();

        verify(restAssistant, never()).executeRequestAndGetJsonBody(any());
    }

    @Test
    @DisplayName("exchangeOrderId가 null이면 조회하지 않는다")
    void skipsNullExchangeOrderId() {
        InFlightOrder order = createInFlightOrder("BUY-BTC-USDT-1", null);
        when(orderTracker.getAll()).thenReturn(List.of(order));
        poller.pollData();

        verify(restAssistant, never()).executeRequestAndGetJsonBody(any());
    }

    @Test
    @DisplayName("활성 주문이 없으면 API를 호출하지 않는다")
    void noActiveOrders() {
        when(orderTracker.getAll()).thenReturn(List.of());
        poller.pollData();
        verify(restAssistant, never()).executeRequestAndGetJsonBody(any());
    }

    @Test
    @DisplayName("여러 주문의 체결 내역이 각각 발행된다")
    void processesTradesForMultipleOrders() {
        InFlightOrder order1 = createInFlightOrder("ORDER-1", "111");
        InFlightOrder order2 = createInFlightOrder("ORDER-2", "222");
        when(orderTracker.getAll()).thenReturn(List.of(order1, order2));

        when(restAssistant.executeRequestAndGetJsonBody(any())).thenAnswer(inv -> {
            RestRequest req = inv.getArgument(0);
            String orderId = (String) req.params().get("orderId");
            return switch (orderId) {
                case "111" -> wrapTrades(List.of(
                        createTradeNode("t1", "111", "50000", "0.01", "0.5", "USDT", true)));
                case "222" -> wrapTrades(List.of(
                        createTradeNode("t2", "222", "51000", "0.02", "1.0", "USDT", false)));
                default -> throw new IllegalArgumentException("unexpected orderId: " + orderId);
            };
        });
        poller.pollData();

        assertThat(testExchangeEventPublisher.getEventsOfType(TradeUpdateEvent.class))
                .hasSize(2)
                .extracting(TradeUpdateEvent::tradeId)
                .containsExactlyInAnyOrder("t1", "t2");
    }


    private void stubTradesResponse(List<ObjectNode> trades) {
        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(wrapTrades(trades));
    }

    private ObjectNode wrapTrades(List<ObjectNode> trades) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode list = root.putObject("result").putArray("list");
        trades.forEach(list::add);
        return root;
    }

    private ObjectNode createTradeNode(String tradeId, String orderId, String price,
                                       String qty, String commission, String commissionAsset,
                                       boolean isMaker) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("execId", tradeId);
        node.put("orderId", orderId);
        node.put("execPrice", price);
        node.put("execQty", qty);
        node.put("execValue", new BigDecimal(price).multiply(new BigDecimal(qty)).toPlainString());
        node.put("execFee", commission);
        node.put("feeCurrency", commissionAsset);
        node.put("isMaker", isMaker);
        node.put("execTime", Instant.now().toEpochMilli());
        return node;
    }
}
