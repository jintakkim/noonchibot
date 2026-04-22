package com.hotak.noonchibot.connector.binance;

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
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }

    public static final int DIFF_SUBSCRIPTION_ID = 1;
    public static final int TRADE_SUBSCRIPTION_ID = 2;

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
                .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                .build();
        JsonNode msg = restAssistant.executeRequestAndGetJsonBody(request);
        long updateId = msg.get("lastUpdateId").asLong();
        Instant eventTime = Instant.ofEpochSecond(msg.get("T").asLong());
        List<OrderBookEntry> bids = parseEntries(msg.get("bids"));
        List<OrderBookEntry> asks = parseEntries(msg.get("asks"));

        return new OrderBookMessage.SnapshotMessage(eventTime, tradingPair, updateId, bids, asks);
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
        String eventType = msg.get("data").get("e").asString();
        return switch (eventType) {
            case "depthUpdate" -> OrderBookMessage.Type.DIFF;
            case "aggTrade" -> OrderBookMessage.Type.TRADE;
            default -> null;
        };
    }

    @Override
    protected OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(data.get("s").asString());
        long firstUpdateId = data.get("U").asLong();
        long updateId = data.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(data.get("E").asLong());
        List<OrderBookEntry> bids = parseEntries(data.get("b"));
        List<OrderBookEntry> asks = parseEntries(data.get("a"));
        return new OrderBookMessage.DiffMessage(eventTime, tradingPair, updateId, bids, asks);
    }

    @Override
    protected OrderBookMessage.TradeMessage parseTradeMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(data.get("s").asString());
        TradeType tradeType = msg.get("m").asBoolean() ? TradeType.SELL : TradeType.BUY;
        return new OrderBookMessage.TradeMessage(
                Instant.ofEpochMilli(data.get("T").asLong()),
                tradingPair,
                data.get("a").asLong(),         // aggregate trade id
                data.get("p").asDecimal(),         // price
                data.get("q").asDecimal(),         // quantity
                tradeType
        );
    }

    private void sendDiffRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> diffMessage = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase) //websocket need lowercase
                .map(symbol -> symbol + "@depth")
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
                .map(symbol -> symbol + "@aggTrade")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
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
}
