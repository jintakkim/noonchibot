package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import com.hotak.noonchibot.core.trade.TradeType;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.ApplicationEventPublisher;
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
    private final TimeSynchronizer timeSynchronizer;
    private final String websocketUrl;

    public OrderBookDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            TimeSynchronizer timeSynchronizer,
            String websocketUrl,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        super(wsAssistant, objectMapper, ioExecutor, taskScheduler, applicationEventPublisher, eventPublisher, eventSubscriber);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.restAssistant = restAssistant;
        this.timeSynchronizer = timeSynchronizer;
        this.websocketUrl = websocketUrl;
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
        List<OrderBookEntry> bids = parseEntries(msg.get("bids"));
        List<OrderBookEntry> asks = parseEntries(msg.get("asks"));

        return new OrderBookEvent.SnapshotReceived(
                tradingPair,
                updateId,
                bids,
                asks,
                Instant.ofEpochMilli(timeSynchronizer.serverTime())
        );
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
        String eventType = msg.get("e").asString();
        return switch (eventType) {
            case "depthUpdate" -> OrderBookMessage.Type.DIFF;
            case "trade" -> OrderBookMessage.Type.TRADE;
            default -> null;
        };
    }

    @Override
    protected OrderBookEvent.DiffReceived parseWsDiffMessage(JsonNode msg) {
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(msg.get("s").asString());
        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        List<OrderBookEntry> bids = parseEntries(msg.get("b"));
        List<OrderBookEntry> asks = parseEntries(msg.get("a"));
        return new OrderBookEvent.DiffReceived(tradingPair, msg.get("u").asLong(), bids, asks, eventTime);
    }

    @Override
    protected List<OrderBookEvent.TradeReceived> parseWsTradeMessage(JsonNode msg) {
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(msg.get("s").asString());
        TradeType tradeType = msg.get("m").asBoolean() ? TradeType.SELL : TradeType.BUY;
        return List.of(new OrderBookEvent.TradeReceived(
                tradingPair,
                msg.get("t").asLong(),
                msg.get("p").asDecimal(),
                msg.get("q").asDecimal(),
                tradeType,
                Instant.ofEpochMilli(msg.get("T").asLong())
        ));
    }

    @Override
    protected OrderBookEvent.SnapshotReceived parseWsSnapshotMessage(JsonNode msg) {
        throw new UnsupportedOperationException("Binance spot WebSocket streams only support diff and trade messages");
    }

    private void sendDiffRequest(String method, Collection<String> tradingPairs) {
        List<String> params = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase)
                .map(symbol -> symbol + "@depth@100ms")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method,
                "params", params,
                "id", DIFF_SUBSCRIPTION_ID
        ), false));
    }

    private void sendTradeRequest(String method, Collection<String> tradingPairs) {
        List<String> params = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase)
                .map(symbol -> symbol + "@trade")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method,
                "params", params,
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
        return URI.create(websocketUrl);
    }
}
