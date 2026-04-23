package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

public class DerivativeOrderBookDataSource extends AbstractOrderBookDataSource {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    private enum Op {
        SUBSCRIBE("subscribe"), UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }

    private static final String ORDERBOOK_TOPIC_PREFIX = "orderbook.";
    private static final String PUBLIC_TRADE_TOPIC_PREFIX = "publicTrade.";
    // linear/inverse: 1, 50, 200, 1000
    private static final int ORDERBOOK_DEPTH = 200;

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;

    public DerivativeOrderBookDataSource(
            WsAssistant wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant
    ) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor, taskScheduler, false);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.restAssistant = restAssistant;
    }

    @Override
    protected OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(DerivativeApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("category", "linear", "symbol", exchangeSymbol, "limit", "200"))
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
        return msg.has("op") && msg.has("success") && !msg.get("success").asBoolean();
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("op") && msg.has("success") && msg.get("success").asBoolean();
    }

    @Override
    protected OrderBookMessage.Type parseMessageType(JsonNode msg) {
        String topic = msg.path("topic").asString();
        String type = msg.path("type").asString();

        if (topic.startsWith(ORDERBOOK_TOPIC_PREFIX)) {
            // 최초 snapshot은 REST 로 받으므로 WS snapshot 은 무시
            if ("snapshot".equals(type)) return null;
            if ("delta".equals(type)) return OrderBookMessage.Type.DIFF;
        }
        if (topic.startsWith(PUBLIC_TRADE_TOPIC_PREFIX)) {
            return OrderBookMessage.Type.TRADE;
        }
        return null;
    }

    @Override
    protected OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(data.get("s").asString());
        long updateId = data.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(msg.get("ts").asLong());
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));
        return new OrderBookMessage.DiffMessage(eventTime, tradingPair, updateId, bids, asks);
    }

    @Override
    protected OrderBookMessage.TradeMessage parseTradeMessage(JsonNode msg) {
        JsonNode data = msg.get("data").get(0); // Bybit는 trade도 배열로 옴
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(data.get("s").asString());
        TradeType tradeType = "Sell".equals(data.get("S").asString()) ? TradeType.SELL : TradeType.BUY;
        return new OrderBookMessage.TradeMessage(
                Instant.ofEpochMilli(data.get("T").asLong()),
                tradingPair,
                data.get("seq").asLong(),
                data.get("p").asDecimal(),
                data.get("v").asDecimal(),
                tradeType
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