package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

class DerivativeOrderBookDataSource extends AbstractOrderBookDataSource {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    private enum Op {
        SUBSCRIBE("subscribe"), UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }

    private static final String ORDERBOOK_TOPIC_PREFIX = "orderbook.";
    private static final String PUBLIC_TRADE_TOPIC_PREFIX = "publicTrade.";
    private static final int ORDERBOOK_DEPTH = 500;  // linear/inverse: 1, 50, 200, 500
    private static final long SNAPSHOT_TIMEOUT_SECONDS = 5;

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    // IO 스레드가 put, 메인 스레드가 take
    private final ConcurrentMap<String, BlockingQueue<OrderBookEvent.SnapshotReceived>> pendingSnapshots
            = new ConcurrentHashMap<>();

    public DerivativeOrderBookDataSource(
            WsAssistantImpl wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistantImpl restAssistant
    ) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor, taskScheduler, false);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    protected OrderBookEvent.SnapshotReceived fetchOrderBookSnapshot(String tradingPair) {
        // IO 스레드가 스냅샷 넣어줄 슬롯 준비
        BlockingQueue<OrderBookEvent.SnapshotReceived> slot = new ArrayBlockingQueue<>(1);
        pendingSnapshots.put(tradingPair, slot);

        try {
            // Bybit는 이미 구독 중이어도 재구독 시 스냅샷 다시 밀어줌
            sendSubscribe(tradingPair);

            OrderBookEvent.SnapshotReceived snapshot = slot.poll(SNAPSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (snapshot == null) {
                throw new RuntimeException("Orderbook snapshot timeout for " + tradingPair);
            }
            return snapshot;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting snapshot: " + tradingPair, e);
        } finally {
            pendingSnapshots.remove(tradingPair);
        }
    }

    @Override
    protected void sendSubscribe(String tradingPair) {
        sendSubscribe(Set.of(tradingPair));
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendRequest(Op.SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(String tradingPair) {
        sendRequest(Op.UNSUBSCRIBE, Set.of(tradingPair));
    }

    private void sendRequest(Op op, Collection<String> tradingPairs) {
        List<String> args = new ArrayList<>(tradingPairs.size() * 2);
        for (String tradingPair : tradingPairs) {
            String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
            args.add(ORDERBOOK_TOPIC_PREFIX + ORDERBOOK_DEPTH + "." + symbol);
            args.add(PUBLIC_TRADE_TOPIC_PREFIX + symbol);
        }
        wsConnection.send(new WsRequest(Map.of(
                "op", op.getApiValue(),
                "args", args
        ), false));
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        // Bybit는 success=false 일 때 에러
        return msg.has("success") && !msg.get("success").asBoolean();
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        // 구독/해제 응답: op 필드 존재 + success=true
        return msg.has("op") && msg.has("success");
    }

    @Override
    protected MessageType parseMessageType(JsonNode msg) {
        String topic = msg.path("topic").asString();
        String type = msg.path("type").asString();

        if (topic.startsWith(ORDERBOOK_TOPIC_PREFIX)) {
            if ("snapshot".equals(type)) return null; // 스냅샷은 processUnknownMessage에서 따로 처리
            if ("delta".equals(type)) return MessageType.DIFF;
        }
        if (topic.startsWith(PUBLIC_TRADE_TOPIC_PREFIX)) {
            return MessageType.TRADE;
        }
        return null;
    }

    @Override
    protected void processUnknownMessage(JsonNode msg) {
        String topic = msg.path("topic").asString();
        String type = msg.path("type").asString();

        if (topic.startsWith(ORDERBOOK_TOPIC_PREFIX) && "snapshot".equals(type)) {
            OrderBookEvent.SnapshotReceived snapshot = parseWsSnapshotEvent(msg);

            // 대기 중인 getOrderBookSnapshot 호출자 깨우기
            BlockingQueue<OrderBookEvent.SnapshotReceived> slot = pendingSnapshots.get(snapshot.tradingPair());
            if (slot != null) {
                slot.offer(snapshot);
            }
        }
    }

    @Override
    protected OrderBookEvent.SnapshotReceived parseWsSnapshotEvent(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(data.get("s").asString());
        long updateId = data.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));
        return new OrderBookEvent.SnapshotReceived(tradingPair, updateId, bids, asks, eventTime);
    }

    @Override
    protected OrderBookEvent.DiffReceived parseWsDiffEvent(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(data.get("s").asString());
        long updateId = data.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));
        return new OrderBookEvent.DiffReceived(tradingPair, updateId, bids, asks, eventTime);
    }

    @Override
    protected List<OrderBookEvent.TradeReceived> parseWsTradeEvents(JsonNode msg) {
        JsonNode data = msg.get("data").get(0); // Bybit는 trade도 배열로 옴
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(data.get("s").asString());
        TradeType tradeType = "Sell".equals(data.get("S").asString()) ? TradeType.SELL : TradeType.BUY;
        return List.of(
                new OrderBookEvent.TradeReceived(
                tradingPair,
                data.get("seq").asLong(),   // sequence
                data.get("p").asDecimal(),  // price
                data.get("v").asDecimal(),  // volume
                tradeType,
                Instant.ofEpochMilli(data.get("T").asLong())
                )
        );
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
