package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
public class BybitOrderBookDataSource extends AbstractOrderBookDataSource {

    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final TimeSynchronizer timeSynchronizer;

    public BybitOrderBookDataSource(
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
                .pathUrl(BybitApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("category", "spot", "symbol", exchangeSymbol, "limit", "200"))
                .build();
        JsonNode result = restAssistant.executeRequestAndGetJsonBody(request).get("result");
        long updateId = result.get("u").asLong();
        Instant ts = Instant.ofEpochMilli(result.get("ts").asLong());
        List<OrderBookEntry> bids = parseEntries(result.get("b"));
        List<OrderBookEntry> asks = parseEntries(result.get("a"));
        return new OrderBookMessage.SnapshotMessage(ts, tradingPair, updateId, bids, asks);
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
        return msg.has("ret_code") && msg.get("ret_code").asInt() != 0;
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("op") && msg.has("success");
    }

    @Override
    protected OrderBookMessage.Type parseMessageType(JsonNode msg) {
        String topic = msg.path("topic").asString();
        if (topic.startsWith("orderbook.")) return OrderBookMessage.Type.DIFF;
        if (topic.startsWith("publicTrade.")) return OrderBookMessage.Type.TRADE;
        return null;
    }

    @Override
    protected OrderBookMessage.TradeMessage parseTradeMessage(JsonNode msg) {
        String topic = msg.get("topic").asString();
        String exchangeSymbol = topic.substring(topic.lastIndexOf('.') + 1);
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());

        // Bybit publicTrade data는 배열이지만 보통 1건씩 옴 — 첫 번째만 사용
        JsonNode trade = msg.get("data").get(0);
        TradeType tradeType = "Buy".equals(trade.get("S").asString()) ? TradeType.BUY : TradeType.SELL;
        BigDecimal price = trade.get("p").asDecimal();
        BigDecimal amount = trade.get("v").asDecimal();
        long tradeId = trade.get("T").asLong(); // timestamp as trade id

        return new OrderBookMessage.TradeMessage(eventTime, tradingPair, tradeId, price, amount, tradeType);
    }

    @Override
    protected OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg) {
        String topic = msg.get("topic").asString();
        String exchangeSymbol = topic.substring(topic.lastIndexOf('.') + 1);
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());

        JsonNode data = msg.get("data");
        long updateId = data.get("u").asLong();
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));

        return new OrderBookMessage.DiffMessage(eventTime, tradingPair, updateId, bids, asks);
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