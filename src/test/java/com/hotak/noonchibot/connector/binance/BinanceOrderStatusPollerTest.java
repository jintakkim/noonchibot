package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.OrderStreamStatus;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BinanceOrderStatusPollerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private RestAssistant mockRestAssistant;
    private OrderTracker mockOrderTracker;
    private TradingPairSymbolRegistry symbolRegistry;
    private BinanceOrderStatusPoller poller;

    @BeforeEach
    void setUp() {
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        mockOrderTracker = Mockito.mock(OrderTracker.class);
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT"));
        poller = new BinanceOrderStatusPoller(
                mockRestAssistant,
                mockOrderTracker,
                symbolRegistry,
                Mockito.mock(OrderStreamStatus.class),
                Runnable::run
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
    @DisplayName("활성 주문의 상태가 업데이트된다")
    void updatesOrderStatus() {
        InFlightOrder order = createActiveOrder("BUY-BTC-USDT-1", "12345");
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of("BUY-BTC-USDT-1", order));
        stubOrderStatusResponse("12345", "PARTIALLY_FILLED");

        poller.pollData();

        ArgumentCaptor<OrderUpdateEvent> captor = ArgumentCaptor.forClass(OrderUpdateEvent.class);
        verify(mockOrderTracker).processOrderUpdate(captor.capture());
        assertThat(captor.getValue().newState()).isEqualTo(InFlightOrder.State.PARTIALLY_FILLED);
        assertThat(captor.getValue().exchangeOrderId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("거래소에서 주문을 찾지 못하면 processOrderNotFound가 호출된다")
    void orderNotFound() {
        InFlightOrder order = createActiveOrder("BUY-BTC-USDT-1", "12345");
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of("BUY-BTC-USDT-1", order));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenThrow(new ExchangeApiException(HttpStatusCode.valueOf(400), "Unknown order sent"));

        poller.pollData();

        verify(mockOrderTracker).processOrderNotFound("BUY-BTC-USDT-1");
    }

    @Test
    @DisplayName("활성 주문이 없으면 API를 호출하지 않는다")
    void noActiveOrders() {
        when(mockOrderTracker.getActiveOrders()).thenReturn(Map.of());

        poller.pollData();

        verify(mockRestAssistant, never()).executeRequestAndGetJsonBody(any(RestRequest.class));
    }

    private void stubOrderStatusResponse(String exchangeOrderId, String status) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("orderId", exchangeOrderId);
        result.put("status", status);
        result.put("updateTime", Instant.now().toEpochMilli());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(result);
    }
}