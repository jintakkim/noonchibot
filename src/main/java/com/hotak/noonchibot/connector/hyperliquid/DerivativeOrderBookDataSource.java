package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

class DerivativeOrderBookDataSource extends AbstractOrderBookDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("subscribe"), UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }

    private final RestAssistantImpl restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public DerivativeOrderBookDataSource(
            WsAssistantImpl wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            RestAssistantImpl restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        super(wsAssistant, DerivativeApiSpec.WS_URL, objectMapper, ioExecutor, taskScheduler, false);
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    protected OrderBookEvent.SnapshotReceived fetchOrderBookSnapshot(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode res = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of("type", "l2Book", "coin", symbol))
                .build());
        return parseSnapshotResponse(res, tradingPair);
    }

    /**
     * rest api version orderBook snapshot spec
     */
    private OrderBookEvent.SnapshotReceived parseSnapshotResponse(JsonNode msg, String tradingPair) {
        long time = msg.get("time").asLong();
        Instant eventTime = Instant.ofEpochMilli(time);

        JsonNode levels = msg.get("levels");
        List<OrderBookEntry> bids = parseLevels(levels.get(0));
        List<OrderBookEntry> asks = parseLevels(levels.get(1));

        // Hyperliquid는 별도 updateId 없음, time을 대용
        return new OrderBookEvent.SnapshotReceived(tradingPair, time, bids, asks, eventTime);
    }


    private static List<OrderBookEntry> parseLevels(JsonNode levelsArray) {
        List<OrderBookEntry> entries = new ArrayList<>(levelsArray.size());
        for (JsonNode level : levelsArray) {
            BigDecimal price = level.get("px").asDecimal();
            BigDecimal size =level.get("sz").asDecimal();
            entries.add(new OrderBookEntry(0L, price, size));
        }
        return entries;
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

    private void sendSnapshotRequest(MessageMethod method, Collection<String> tradingPairs) {
        for (String tradingPair : tradingPairs) {
            String coin = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
            Map<String, Object> payload = Map.of(
                    "method", method.getApiValue(),
                    "subscription", Map.of(
                            "type", "l2Book",
                            "coin", coin
                    )
            );
            wsConnection.send(new WsRequest(payload, false));
        }
    }

    private void sendTradesRequest(MessageMethod method, Collection<String> tradingPairs) {
        for (String tradingPair : tradingPairs) {
            String coin = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
            Map<String, Object> payload = Map.of(
                    "method", method.getApiValue(),
                    "subscription", Map.of(
                            "type", "trades",
                            "coin", coin
                    )
            );
            wsConnection.send(new WsRequest(payload, false));
        }
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
    protected MessageType parseMessageType(JsonNode msg) {
        JsonNode channelNode = msg.get("channel");
        if (channelNode == null) return null;

        String channel = channelNode.asString();
        return switch (channel) {
            case "l2Book" -> MessageType.SNAPSHOT;
            case "trades" -> MessageType.TRADE;
            default -> null;  // subscriptionResponse, error, pong 등은 null
        };
    }

    @Override
    protected OrderBookEvent.DiffReceived parseWsDiffEvent(JsonNode msg) {
        throw new UnsupportedOperationException("hyperliquid WebSocket streams only support diff and trade messages");
    }

    @Override
    protected List<OrderBookEvent.TradeReceived> parseWsTradeEvents(JsonNode msg) {
        JsonNode data = msg.get("data");  // 배열
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
                trade.get("px").asDecimal(),     // 가격
                trade.get("sz").asDecimal(),     // 수량
                tradeType,
                Instant.ofEpochMilli(trade.get("time").asLong())
        );
    }

    @Override
    protected OrderBookEvent.SnapshotReceived parseWsSnapshotEvent(JsonNode msg) {
        JsonNode data = msg.get("data");
        String coin = data.get("coin").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin);
        long time = data.get("time").asLong();
        Instant eventTime = Instant.ofEpochMilli(time);

        JsonNode levels = data.get("levels");
        List<OrderBookEntry> bids = parseLevels(levels.get(0));
        List<OrderBookEntry> asks = parseLevels(levels.get(1));

        long updateId = time;  // Hyperliquid는 별도 updateId 없음, time으로 대용
        return new OrderBookEvent.SnapshotReceived(tradingPair, updateId, bids, asks, eventTime);
    }
}
