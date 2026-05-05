package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class DerivativeOrderBookDataSourceTestTest extends DiffSupportingOrderBookDataSourceTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
            Map.of("BTC-USDT", "BTCUSDT", "ETH-USDT", "ETHUSDT")
    );

    private RestAssistant restAssistant;

    public DerivativeOrderBookDataSourceTestTest() {
        super("USDT");
    }

    @Override
    protected AbstractOrderBookDataSource createDataSource(WsAssistant wsAssistant, IoExecutor ioExecutor, TaskScheduler taskScheduler) {
        restAssistant = Mockito.mock(RestAssistant.class);
        return new DerivativeOrderBookDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_PUBLIC_URL,
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
        ack.putNull("result");
        ack.put("id", 1);
        return new WsResponse(ack.toString(), WsResponse.MessageType.TEXT);
    }

    @Override
    protected JsonNode createErrorNode(String errorMsg) {
        ObjectNode error = objectMapper.createObjectNode();
        ObjectNode errorDetail = objectMapper.createObjectNode();
        errorDetail.put("msg", errorMsg);
        error.set("error", errorDetail);
        return error;
    }

    @Override
    protected JsonNode createDiffMessageNode(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("stream", symbol.toLowerCase() + "@depth");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("e", "depthUpdate");
        data.put("E", Instant.now().toEpochMilli());
        data.put("T", Instant.now().toEpochMilli());
        data.put("s", symbol);
        data.put("U", 100L);
        data.put("u", 101L);
        data.put("pu", 99L);

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

        wrapper.set("data", data);
        return wrapper;
    }

    @Override
    protected JsonNode createTradeMessageNode(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("stream", symbol.toLowerCase() + "@aggTrade");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("e", "aggTrade");
        data.put("E", Instant.now().toEpochMilli());
        data.put("s", symbol);
        data.put("a", 12345L);
        data.put("p", "50000.00");
        data.put("q", "0.500");
        data.put("T", Instant.now().toEpochMilli());
        data.put("m", true); // isBuyerMaker → SELL

        wrapper.set("data", data);
        wrapper.put("m", true);
        return wrapper;
    }

    @Test
    @DisplayName("새 오더북 요청 시 스냅샷이 적용된 OrderBook을 반환한다")
    void getNewOrderBookAppliesSnapshot() {
        stubSnapshotResponse();

        OrderBook orderBook = dataSource.getNewOrderBook("BTC-USDT");

        assertThat(orderBook).isNotNull();
        assertThat(orderBook.getBestBid()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(orderBook.getBestAsk()).isEqualByComparingTo(new BigDecimal("50001.00"));
        assertThat(orderBook.getSnapshotId()).isEqualTo(100L);
    }

    private void stubSnapshotResponse() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("lastUpdateId", 100L);
        snapshot.put("T", Instant.now().getEpochSecond());

        ArrayNode bids = objectMapper.createArrayNode();
        ArrayNode bid = objectMapper.createArrayNode();
        bid.add("50000.00");
        bid.add("1.000");
        bids.add(bid);
        snapshot.set("bids", bids);

        ArrayNode asks = objectMapper.createArrayNode();
        ArrayNode ask = objectMapper.createArrayNode();
        ask.add("50001.00");
        ask.add("0.500");
        asks.add(ask);
        snapshot.set("asks", asks);

        when(restAssistant.executeRequestAndGetJsonBody(any())).thenReturn(snapshot);
    }

    @Test
    @DisplayName("최초 구독 시 스트림 반환, 구독 메시지 전송 및 1시간 주기 스냅샷 스케줄러가 등록된다")
    protected void subscribeOrderBookStreamFirstTime() {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream("BTC-USDT");
        assertThat(stream).isNotNull();
        assertThat(stream.tradingPair).isEqualTo("BTC-USDT");
        // 구독 메시지 전송 확인
        verify(mockWsConnection, times(2)).send(any(WsRequest.class));
        // 1시간 주기 스케줄러 등록 확인
        verify(mockTaskScheduler, times(1)).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(Duration.ofHours(1))
        );
    }

    @Test
    @DisplayName("같은 페어를 여러 번 구독하면 다른 스트림이 반환되지만, 서버에는 최초 1번만 구독 요청을 보낸다")
    void multipleSubscriptionsReturnDifferentStreamsButOneRequest() {
        OrderBookMessageStream stream1 = dataSource.subscribeOrderBookStream("BTC-USDT");
        OrderBookMessageStream stream2 = dataSource.subscribeOrderBookStream("BTC-USDT");
        assertThat(stream1).isNotSameAs(stream2);
        // diff + trade message once
        verify(mockWsConnection, times(2)).send(any());
        verify(mockTaskScheduler, times(1)).scheduleAtFixedRate(any(), any(), any());
    }

    @Test
    @DisplayName("마지막 스트림이 구독 해제되면 서버에 구독 해제 요청(Unsubscribe)을 보낸다")
    void unsubscribeRemovesLastStream() {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream("BTC-USDT");
        dataSource.unsubscribe(stream);
        // 구독 1번(diff + trade) + 해제 1번(diff + trade) = 총 2번 전송
        verify(mockWsConnection, times(4)).send(any());
    }

    @Test
    @DisplayName("여러 구독 중 일부만 해제하면 서버에 구독 해제 요청을 보내지 않는다")
    void unsubscribeOneKeepsOthers() {
        OrderBookMessageStream stream1 = dataSource.subscribeOrderBookStream("BTC-USDT");
        OrderBookMessageStream stream2 = dataSource.subscribeOrderBookStream("BTC-USDT");
        dataSource.unsubscribe(stream1);
        // 스트림은 제거되었지만, 구독 메시지는 최초 1번(diff + trade = 2)만 전송되었고 해제 메시지는 안 감
        verify(mockWsConnection, times(2)).send(any());
    }
}
