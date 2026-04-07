package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSourceTest;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BinanceDerivativeOrderBookDataSourceTest extends AbstractOrderBookDataSourceTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
            Map.of("BTC-USDT", "BTCUSDT", "ETH-USDT", "ETHUSDT")
    );

    private RestAssistant restAssistant;

    @Override
    protected AbstractOrderBookDataSource createDataSource(WsAssistant wsAssistant, IoExecutor ioExecutor, TaskScheduler taskScheduler) {
        restAssistant = Mockito.mock(RestAssistant.class);
        return new BinanceDerivativeOrderBookDataSource(
                wsAssistant,
                BinanceDerivativeApiSpec.WSS_PUBLIC_URL,
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
}
