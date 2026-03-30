package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.OrderStreamStatus;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BinanceTradePollerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private RestAssistant mockRestAssistant;
    private OrderTracker mockOrderTracker;
    private TradingPairSymbolRegistry symbolRegistry;
    private BinanceTradePoller poller;

    @BeforeEach
    void setUp() {
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        mockOrderTracker = Mockito.mock(OrderTracker.class);
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT"));
        poller = new BinanceTradePoller(
                Mockito.mock(OrderStreamStatus.class),
                Runnable::run,
                mockRestAssistant,
                mockOrderTracker,
                symbolRegistry
        );
    }

    private InFlightOrder createActiveOrder(String clientOrderId, String exchangeOrderId) {
        return new InFlightOrder(
                clientOrderId, "BTC-USDT", OrderType.LIMIT,
                TradeType.BUY, new BigDecimal("0.01"), new BigDecimal("50000"),
                Instant.now(), exchangeOrderId
        );
    }

    @Test
    @DisplayName("체결 내역이 OrderTracker에 전달된다")
    void processesTradeUpdates() {
        InFlightOrder inFlightOrder = createActiveOrder("BUY-BTC-USDT-1", "12345");
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of("BUY-BTC-USDT-1", inFlightOrder));
        stubTradesResponse(List.of(
                createTradeNode("t1", "12345", "50000", "0.005", "0.25", "USDT", true)
        ));

        poller.pollData();

        ArgumentCaptor<TradeUpdateEvent> captor = ArgumentCaptor.forClass(TradeUpdateEvent.class);
        verify(mockOrderTracker).processTradeUpdate(captor.capture());
        TradeUpdateEvent trade = captor.getValue();
        assertThat(trade.tradeId()).isEqualTo("t1");
        assertThat(trade.fillPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(trade.fillBaseAmount()).isEqualByComparingTo(new BigDecimal("0.005"));
        assertThat(trade.isMaker()).isTrue();
    }

    @Test
    @DisplayName("여러 체결 내역이 모두 전달된다")
    void processesMultipleTradeUpdates() {
        InFlightOrder inFlightOrder = createActiveOrder("BUY-BTC-USDT-1", "12345");
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of("BUY-BTC-USDT-1", inFlightOrder));
        stubTradesResponse(List.of(
                createTradeNode("t1", "12345", "50000", "0.003", "0.15", "USDT", true),
                createTradeNode("t2", "12345", "50100", "0.007", "0.35", "USDT", false)
        ));

        poller.pollData();

        verify(mockOrderTracker, times(2)).processTradeUpdate(any(TradeUpdateEvent.class));
    }

    @Test
    @DisplayName("exchangeOrderId가 UNKNOWN이면 체결 내역을 조회하지 않는다")
    void skipsUnknownExchangeOrderId() {
        InFlightOrder inFlightOrder = createActiveOrder("BUY-BTC-USDT-1", "UNKNOWN");
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of("BUY-BTC-USDT-1", inFlightOrder));

        poller.pollData();

        verify(mockRestAssistant, never()).executeRequestAndGetJsonBody(any(RestRequest.class));
    }

    @Test
    @DisplayName("exchangeOrderId가 null이면 체결 내역을 조회하지 않는다")
    void skipsNullExchangeOrderId() {
        InFlightOrder inFlightOrder = createActiveOrder("BUY-BTC-USDT-1", null);
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of("BUY-BTC-USDT-1", inFlightOrder));

        poller.pollData();

        verify(mockRestAssistant, never()).executeRequestAndGetJsonBody(any(RestRequest.class));
    }

    @Test
    @DisplayName("활성 주문이 없으면 API를 호출하지 않는다")
    void noActiveOrders() {
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of());

        poller.pollData();

        verify(mockRestAssistant, never()).executeRequestAndGetJsonBody(any(RestRequest.class));
    }

    private void stubTradesResponse(List<ObjectNode> trades) {
        ArrayNode array = objectMapper.createArrayNode();
        trades.forEach(array::add);
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(array);
    }

    private ObjectNode createTradeNode(String tradeId, String orderId, String price,
                                       String qty, String commission, String commissionAsset,
                                       boolean isMaker) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", tradeId);
        node.put("orderId", orderId);
        node.put("price", price);
        node.put("qty", qty);
        node.put("quoteQty", new BigDecimal(price).multiply(new BigDecimal(qty)).toPlainString());
        node.put("commission", commission);
        node.put("commissionAsset", commissionAsset);
        node.put("isMaker", isMaker);
        node.put("time", Instant.now().toEpochMilli());
        return node;
    }
}