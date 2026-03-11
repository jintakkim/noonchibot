package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.OrderBookMessageStream;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
public class BinanceOrderBookDataSource extends AbstractOrderBookDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }

    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public BinanceOrderBookDataSource(
            TaskScheduler taskScheduler,
            WsAssistant wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            String connectionThreadName,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant
    ) {
        super(taskScheduler, wsAssistant, publicWsUrl, objectMapper, connectionThreadName);
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }


    @Override
    protected OrderBook createOrderBook() {
        return new BinanceOrderBook();
    }

    @Override
    protected OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                .build();
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(request);
        return BinanceOrderBook.snapshotMessageFromExchange(body, tradingPair);
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
        return BinanceOrderBook.tradeMessageFromExchange(msg, tradingPair);
    }

    protected OrderBookMessage.DiffMessage parseDiffMessage(JsonNode msg) {
        String exchangeSymbol = msg.get("s").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
        return BinanceOrderBook.diffMessageFromExchange(msg, tradingPair);
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
                "id", 2
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
                "id", 2
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
}
