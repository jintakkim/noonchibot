package com.hotak.noonchibot.connector.hyperliquid;

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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("subscribe"), UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }

    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final String websocketUrl;

    public OrderBookDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            String websocketUrl,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        super(wsAssistant, objectMapper, ioExecutor, taskScheduler, applicationEventPublisher, eventPublisher, eventSubscriber);
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.websocketUrl = websocketUrl;
    }

    @Override
    protected URI connectionUri() {
        return URI.create(websocketUrl);
    }

    @Override
    protected OrderBookEvent.SnapshotReceived fetchOrderBookSnapshot(String tradingPair) {
        String coin = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode res = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of("type", "l2Book", "coin", coin))
                .build());
        return parseSnapshotResponse(res, tradingPair);
    }

    private OrderBookEvent.SnapshotReceived parseSnapshotResponse(JsonNode msg, String tradingPair) {
        long time = msg.get("time").asLong();
        JsonNode levels = msg.get("levels");
        return new OrderBookEvent.SnapshotReceived(
                tradingPair,
                time,
                parseLevels(levels.get(0)),
                parseLevels(levels.get(1)),
                Instant.ofEpochMilli(time)
        );
    }

    @Override
    protected void sendSubscribe(String tradingPair) {
        sendSubscribe(Set.of(tradingPair));
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendSnapshotRequest(MessageMethod.SUBSCRIBE, tradingPairs);
        sendTradesRequest(MessageMethod.SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(String tradingPair) {
        sendTradesRequest(MessageMethod.UNSUBSCRIBE, Set.of(tradingPair));
        sendSnapshotRequest(MessageMethod.UNSUBSCRIBE, Set.of(tradingPair));
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        return msg.has("channel") && "error".equals(msg.get("channel").asString());
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("channel") && "subscriptionResponse".equals(msg.get("channel").asString());
    }

    @Override
    protected OrderBookMessage.Type parseMessageType(JsonNode msg) {
        JsonNode channelNode = msg.get("channel");
        if (channelNode == null) return null;
        return switch (channelNode.asString()) {
            case "l2Book" -> OrderBookMessage.Type.SNAPSHOT;
            case "trades" -> OrderBookMessage.Type.TRADE;
            default -> null;
        };
    }

    @Override
    protected OrderBookEvent.DiffReceived parseWsDiffMessage(JsonNode msg) {
        throw new UnsupportedOperationException("Hyperliquid l2Book stream sends snapshots, not diffs");
    }

    @Override
    protected List<OrderBookEvent.TradeReceived> parseWsTradeMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        List<OrderBookEvent.TradeReceived> trades = new ArrayList<>(data.size());
        for (JsonNode trade : data) {
            trades.add(parseTrade(trade));
        }
        return trades;
    }

    private OrderBookEvent.TradeReceived parseTrade(JsonNode trade) {
        String coin = trade.get("coin").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin);
        TradeType tradeType = "A".equals(trade.get("side").asString()) ? TradeType.SELL : TradeType.BUY;
        return new OrderBookEvent.TradeReceived(
                tradingPair,
                trade.get("tid").asLong(),
                trade.get("px").asDecimal(),
                trade.get("sz").asDecimal(),
                tradeType,
                Instant.ofEpochMilli(trade.get("time").asLong())
        );
    }

    @Override
    protected OrderBookEvent.SnapshotReceived parseWsSnapshotMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(data.get("coin").asString());
        return parseSnapshotResponse(data, tradingPair);
    }

    private void sendSnapshotRequest(MessageMethod method, Collection<String> tradingPairs) {
        for (String tradingPair : tradingPairs) {
            sendSubscription(method, "l2Book", tradingPair);
        }
    }

    private void sendTradesRequest(MessageMethod method, Collection<String> tradingPairs) {
        for (String tradingPair : tradingPairs) {
            sendSubscription(method, "trades", tradingPair);
        }
    }

    private void sendSubscription(MessageMethod method, String type, String tradingPair) {
        String coin = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
                "subscription", Map.of(
                        "type", type,
                        "coin", coin
                )
        ), false));
    }

    private static List<OrderBookEntry> parseLevels(JsonNode levelsArray) {
        List<OrderBookEntry> entries = new ArrayList<>(levelsArray.size());
        for (JsonNode level : levelsArray) {
            entries.add(new OrderBookEntry(
                    level.has("n") ? level.get("n").asLong() : 0,
                    level.get("px").asDecimal(),
                    level.get("sz").asDecimal()
            ));
        }
        return entries;
    }
}
