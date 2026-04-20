package com.hotak.noonchibot.connector.derivative.bybit;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSourceTest;
import com.hotak.noonchibot.core.orderbook.OrderBookMessageStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class BybitDerivativeOrderBookDataSourceTest extends AbstractOrderBookDataSourceTest {

    private static final TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
            Map.of("BTC-USDT", "BTCUSDT", "ETH-USDT", "ETHUSDT")
    );

    private RestAssistant restAssistant;

    @Override
    protected AbstractOrderBookDataSource createDataSource(
            WsAssistant wsAssistant,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler
    ) {
        restAssistant = Mockito.mock(RestAssistant.class);
        return new BybitDerivativeOrderBookDataSource(
                wsAssistant,
                BybitDerivativeApiSpec.WSS_LINEAR_URL,
                objectMapper,
                ioExecutor,
                taskScheduler,
                tradingPairSymbolRegistry,
                restAssistant
        );
    }

    @Override
    protected WsResponse createAckResponse() {
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("success", true);
        ack.put("ret_msg", "");
        ack.put("op", "subscribe");
        ack.put("conn_id", "test-conn-id");
        return new WsResponse(ack.toString(), WsResponse.MessageType.TEXT);
    }

    @Override
    protected JsonNode createErrorNode(String errorMsg) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("ret_msg", errorMsg);
        error.put("op", "subscribe");
        return error;
    }

    @Override
    protected JsonNode createDiffMessageNode(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("topic", "orderbook.500." + symbol);
        wrapper.put("type", "delta");
        wrapper.put("ts", Instant.now().toEpochMilli());

        ObjectNode data = objectMapper.createObjectNode();
        data.put("s", symbol);

        ArrayNode bids = objectMapper.createArrayNode();
        ArrayNode bid = objectMapper.createArrayNode();
        bid.add("50000.00");
        bid.add("1.000");
        bids.add(bid);
        data.set("b", bids);

        ArrayNode asks = objectMapper.createArrayNode();
        ArrayNode ask = objectMapper.createArrayNode();
        ask.add("50001.00");
        ask.add("0.500");
        asks.add(ask);
        data.set("a", asks);

        data.put("u", 101L);
        data.put("seq", 7961638724L);

        wrapper.set("data", data);
        return wrapper;
    }

    @Override
    protected JsonNode createTradeMessageNode(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("topic", "publicTrade." + symbol);
        wrapper.put("type", "snapshot");
        wrapper.put("ts", Instant.now().toEpochMilli());

        ArrayNode data = objectMapper.createArrayNode();
        ObjectNode trade = objectMapper.createObjectNode();
        trade.put("T", Instant.now().toEpochMilli());
        trade.put("s", symbol);
        trade.put("S", "Buy");
        trade.put("v", "0.001");
        trade.put("p", "50000.00");
        trade.put("L", "PlusTick");
        trade.put("i", "20f43950-d8dd-5b31-9112-a178eb6023af");
        trade.put("BT", false);
        trade.put("seq", 7961638724L);
        data.add(trade);

        wrapper.set("data", data);
        return wrapper;
    }


    @Test
    @DisplayName("최초 구독 시 orderbook+trade가 하나의 메시지로 전송된다")
    void subscribeOrderBookStreamFirstTime() {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream("BTC-USDT");
        assertThat(stream).isNotNull();
        assertThat(stream.tradingPair).isEqualTo("BTC-USDT");
        // Bybit은 orderbook과 trade 토픽을 args 배열에 묶어서 send()를 1번 호출
        verify(mockWsConnection, times(1)).send(any(WsRequest.class));
        verify(mockTaskScheduler, times(1)).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(Duration.ofHours(1))
        );
    }

    @Test
    @DisplayName("같은 페어 다중 구독 시에도 send는 한 번만 호출된다")
    void multipleSubscriptionsReturnDifferentStreamsButOneRequest() {
        OrderBookMessageStream stream1 = dataSource.subscribeOrderBookStream("BTC-USDT");
        OrderBookMessageStream stream2 = dataSource.subscribeOrderBookStream("BTC-USDT");
        assertThat(stream1).isNotSameAs(stream2);
        verify(mockWsConnection, times(1)).send(any());
        verify(mockTaskScheduler, times(1)).scheduleAtFixedRate(any(), any(), any());
    }

    @Test
    @DisplayName("마지막 스트림 해제 시 subscribe 1회 + unsubscribe 1회 = 총 2회")
    void unsubscribeRemovesLastStream() {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream("BTC-USDT");
        dataSource.unsubscribe(stream);
        // subscribe 1번 + unsubscribe 1번
        verify(mockWsConnection, times(2)).send(any());
    }

    @Test
    @DisplayName("일부 스트림만 해제 시 서버 unsubscribe는 보내지 않는다")
    void unsubscribeOneKeepsOthers() {
        OrderBookMessageStream stream1 = dataSource.subscribeOrderBookStream("BTC-USDT");
        OrderBookMessageStream stream2 = dataSource.subscribeOrderBookStream("BTC-USDT");
        dataSource.unsubscribe(stream1);
        // 구독 1번만 전송, 해제 메시지는 안 감
        verify(mockWsConnection, times(1)).send(any());
    }
}