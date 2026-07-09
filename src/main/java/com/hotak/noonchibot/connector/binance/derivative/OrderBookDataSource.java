package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;

class OrderBookDataSource extends AbstractOrderBookDataSource {
    public static final int DIFF_SUBSCRIPTION_ID = 1;
    public static final int TRADE_SUBSCRIPTION_ID = 2;

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;

    public OrderBookDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        super(wsAssistant, objectMapper, ioExecutor, taskScheduler, eventPublisher, eventSubscriber);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.restAssistant = restAssistant;
    }

    @Override
    protected OrderBookEvent.SnapshotReceived fetchOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                .build();
        JsonNode msg = restAssistant.executeRequestAndGetJsonBody(request);
        long updateId = msg.get("lastUpdateId").asLong();
        Instant eventTime = Instant.ofEpochMilli(msg.get("T").asLong());
        List<OrderBookEntry> bids = parseEntries(msg.get("bids"));
        List<OrderBookEntry> asks = parseEntries(msg.get("asks"));

        return new OrderBookEvent.SnapshotReceived(tradingPair, updateId, bids, asks, eventTime);
    }

    @Override
    protected void sendSubscribe(String tradingPair) {
        sendSubscribe(Set.of(tradingPair));
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendTradeRequest(ApiSpec.WS_SUBSCRIBE, tradingPairs);
        sendDiffRequest(ApiSpec.WS_SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(String tradingPair) {
        sendTradeRequest(ApiSpec.WS_UNSUBSCRIBE, List.of(tradingPair));
        sendDiffRequest(ApiSpec.WS_UNSUBSCRIBE, List.of(tradingPair));
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
        String eventType = msg.get("data").get("e").asString();
        return switch (eventType) {
            case "depthUpdate" -> OrderBookMessage.Type.DIFF;
            case "aggTrade" -> OrderBookMessage.Type.TRADE;
            default -> null;
        };
    }

    @Override
    protected OrderBookEvent.DiffReceived parseWsDiffMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(data.get("s").asString());
        long firstUpdateId = data.get("U").asLong();
        long lastUpdateId = data.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(data.get("E").asLong());
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));
        return new OrderBookEvent.DiffReceived(tradingPair, lastUpdateId, bids, asks, eventTime);
    }

    @Override
    protected List<OrderBookEvent.TradeReceived> parseWsTradeMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(data.get("s").asString());
        TradeType tradeType = data.get("m").asBoolean() ? TradeType.SELL : TradeType.BUY;
        return List.of(new OrderBookEvent.TradeReceived(
                    tradingPair,
                    data.get("a").asLong(),         // aggregate trade id
                    data.get("p").asDecimal(),         // price
                    data.get("q").asDecimal(),         // quantity
                    tradeType,
                    Instant.ofEpochMilli(data.get("T").asLong())
        ));
    }

    @Override
    protected OrderBookEvent.SnapshotReceived parseWsSnapshotMessage(JsonNode msg) {
        throw new UnsupportedOperationException("Binance WebSocket streams only support diff and trade messages");
    }

    private void sendDiffRequest(String method, Collection<String> tradingPairs) {
        List<String> diffMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@depth")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method,
                "params", diffMessage,
                "id", DIFF_SUBSCRIPTION_ID
        ), false));
    }

    private void sendTradeRequest(String method, Collection<String> tradingPairs) {
        List<String> tradeMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@aggTrade")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method,
                "params", tradeMessage,
                "id", TRADE_SUBSCRIPTION_ID
        ), false));
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

    @Override
    protected URI connectionUri() {
        return URI.create(ApiSpec.WSS_PUBLIC_URL);
    }
}
