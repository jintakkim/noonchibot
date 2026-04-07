package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
public class BinanceOrderBookDataSource extends AbstractOrderBookDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }
    public static final int DIFF_SUBSCRIPTION_ID = 1;
    public static final int TRADE_SUBSCRIPTION_ID = 2;

    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final TimeSynchronizer timeSynchronizer;

    public BinanceOrderBookDataSource(
            WsAssistant wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            TimeSynchronizer timeSynchronizer
    ) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor, taskScheduler, false);
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.timeSynchronizer = timeSynchronizer;
    }

    @Override
    protected OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                .build();
        JsonNode msg = restAssistant.executeRequestAndGetJsonBody(request);
        long updateId = msg.get("lastUpdateId").asLong();
        List<OrderBookEntry> bids = parseEntries(msg.get("bids"));
        List<OrderBookEntry> asks = parseEntries(msg.get("asks"));
        return new OrderBookMessage.SnapshotMessage(Instant.ofEpochMilli(timeSynchronizer.serverTime()), tradingPair, updateId, bids, asks);
    }

    @Override
    protected void sendSubscribe(String tradingPair) {
        sendSubscribe(Set.of(tradingPair));
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendTradeRequest(MessageMethod.SUBSCRIBE, tradingPairs);
        sendDiffRequest(MessageMethod.SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(String tradingPair) {
        sendTradeRequest(MessageMethod.UNSUBSCRIBE, List.of(tradingPair));
        sendDiffRequest(MessageMethod.UNSUBSCRIBE, List.of(tradingPair));
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        return msg.has("error");
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("id") && msg.has("result");
    }

    @Override
    protected OrderBookMessage.Type parseMessageType(JsonNode msg) {
        String eventType = msg.path("e").asString();
        return switch (eventType) {
            case "depthUpdate" -> OrderBookMessage.Type.DIFF;
            case "trade" -> OrderBookMessage.Type.TRADE;
            default -> null;
        };
    }

    protected OrderBookMessage.TradeMessage parseTradeMessage(JsonNode msg) {
        String exchangeSymbol = msg.get("s").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        // m=true → maker가 buyer → taker는 seller (SELL), m=false → BUY
        TradeType tradeType = msg.get("m").asBoolean() ? TradeType.SELL : TradeType.BUY;
        long tradeId = msg.get("t").asLong();
        BigDecimal price = msg.get("p").asDecimal();
        BigDecimal amount = msg.get("q").asDecimal();
        return new OrderBookMessage.TradeMessage(eventTime, tradingPair, tradeId, price, amount, tradeType);
    }

    protected OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg) {
        String exchangeSymbol = msg.get("s").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        long firstUpdateId = msg.get("U").asLong();
        long updateId = msg.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        List<OrderBookEntry> bids = parseEntries(msg.get("b"));
        List<OrderBookEntry> asks = parseEntries(msg.get("a"));

        return new OrderBookMessage.DiffMessage(eventTime, tradingPair, updateId, bids, asks);
    }

    private void sendDiffRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> diffMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@depth@100ms")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
                "params", diffMessage,
                "id", DIFF_SUBSCRIPTION_ID
        ), false));
    }

    private void sendTradeRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> tradeMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@trade")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
                "params", tradeMessage,
                "id", TRADE_SUBSCRIPTION_ID
        ), false));
    }

    /**
     * using rest api
     */
    @Override
    public Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs) {
        if(tradingPairs == null || tradingPairs.isEmpty()) throw new IllegalArgumentException("한개 이상의 tradingPair가 전달되어야 합니다.");
        List<String> symbols = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .toList();

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                .params(Map.of("symbols", symbols))
                .weightOverrides(Map.of("REQUEST_WEIGHT", BinanceApiSpec.getTickerPriceChangeDynamicWeight(symbols.size())))
                .build();

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);

        Map<String, BigDecimal> result = new HashMap<>();
        for (JsonNode ticker : response) {
            String exchangeSymbol = ticker.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
            result.put(tradingPair, ticker.get("price").asDecimal());
        }
        return result;
    }

    /**
     * using rest api
     */
    @Override
    public BigDecimal getLastTradedPrice(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol))
                .build();
        return restAssistant.executeRequestAndGetJsonBody(request).get("lastPrice").asDecimal();
    }

    private static List<OrderBookEntry> parseEntries(JsonNode arrayNode) {
        List<OrderBookEntry> entries = new ArrayList<>(arrayNode.size());
        for (JsonNode entry : arrayNode) {
            BigDecimal price = entry.get(0).asDecimal();
            BigDecimal amount = entry.get(1).asDecimal();
            entries.add(new OrderBookEntry(0L, price, amount));
        }
        return entries;
    }
}
