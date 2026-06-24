package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
class SpotOrderBookDataSource extends AbstractOrderBookDataSource {

    private final RestAssistantImpl restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public SpotOrderBookDataSource(
            WsAssistantImpl wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistantImpl restAssistant
    ) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor, taskScheduler, false);
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    protected OrderBookEvent.SnapshotReceived fetchOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(SpotApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("category", "spot", "symbol", exchangeSymbol, "limit", "200"))
                .build();
        JsonNode result = restAssistant.executeRequestAndGetJsonBody(request).get("result");
        long updateId = result.get("u").asLong();
        Instant ts = Instant.ofEpochMilli(result.get("ts").asLong());
        List<OrderBookEntry> bids = parseEntries(result.get("b"));
        List<OrderBookEntry> asks = parseEntries(result.get("a"));
        return new OrderBookEvent.SnapshotReceived(tradingPair, updateId, bids, asks, ts);
    }

    @Override
    protected void sendSubscribe(String tradingPair) {
        sendSubscribe(Set.of(tradingPair));
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendRequest("subscribe", tradingPairs, "orderbook.50.", "publicTrade.");
    }

    @Override
    protected void sendUnsubscribe(String tradingPair) {
        sendRequest("unsubscribe", List.of(tradingPair), "orderbook.50.", "publicTrade.");
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        return msg.has("op") && msg.has("success") && !msg.get("success").asBoolean();
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("op") && msg.has("success") && msg.get("success").asBoolean();
    }

    @Override
    protected MessageType parseMessageType(JsonNode msg) {
        String topic = msg.path("topic").asString();
        if (topic.startsWith("orderbook.")) return MessageType.DIFF;
        if (topic.startsWith("publicTrade.")) return MessageType.TRADE;
        return null;
    }

    @Override
    protected List<OrderBookEvent.TradeReceived> parseWsTradeEvents(JsonNode msg) {
        String topic = msg.get("topic").asString();
        String exchangeSymbol = topic.substring(topic.lastIndexOf('.') + 1);
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());

        JsonNode trade = msg.get("data").get(0);
        TradeType tradeType = "Buy".equals(trade.get("S").asString()) ? TradeType.BUY : TradeType.SELL;
        BigDecimal price = trade.get("p").asDecimal();
        BigDecimal amount = trade.get("v").asDecimal();
        long tradeId = trade.get("T").asLong();

        return List.of(new OrderBookEvent.TradeReceived(tradingPair, tradeId, price, amount, tradeType, eventTime));
    }

    @Override
    protected OrderBookEvent.SnapshotReceived parseWsSnapshotEvent(JsonNode msg) {
        throw new UnsupportedOperationException("Bybit WebSocket streams only support diff and trade messages");
    }

    @Override
    protected OrderBookEvent.DiffReceived parseWsDiffEvent(JsonNode msg) {
        String topic = msg.get("topic").asString();
        String exchangeSymbol = topic.substring(topic.lastIndexOf('.') + 1);
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());

        JsonNode data = msg.get("data");
        long updateId = data.get("u").asLong();
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));

        return new OrderBookEvent.DiffReceived(tradingPair, updateId, bids, asks, eventTime);
    }

    private void sendRequest(String op, Collection<String> tradingPairs, String... prefixes) {
        List<String> args = new ArrayList<>();
        for (String tradingPair : tradingPairs) {
            String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
            for (String prefix : prefixes) {
                args.add(prefix + symbol);
            }
        }
        wsConnection.send(new WsRequest(Map.of(
                "op", op,
                "args", args
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
}
