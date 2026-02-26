package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.OrderBookMessageStream;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class BinanceOrderBookDataSource implements OrderBookDataSource {
    private final RestAssistant restAssistant;
    private final WsAssistant wsAssistant;
    private WsConnection wsConnection;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final Map<String, Set<OrderBookMessageStream>> orderBookMessageStreams;

    public BinanceOrderBookDataSource(RestAssistant restAssistant, WsAssistant wsAssistant, TradingPairSymbolRegistry tradingPairSymbolRegistry) {
        this.restAssistant = restAssistant;
        this.wsConnection = wsAssistant.connect(URI.create(BinanceApiSpec.WSS_URL), );
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        orderBookMessageStreams = new ConcurrentHashMap<>();

    }


    @Override
    public OrderBook getNewOrderBook(String tradingPair) {
         BinanceOrderBook orderBook = new BinanceOrderBook();
         OrderBookMessage.SnapshotMessage msg = getOrderBookSnapshot(tradingPair);
         orderBook.applySnapshot(msg.getBids(), msg.getAsks(), msg.getUpdateId());
         return orderBook;
    }

    private OrderBookMessage.SnapshotMessage getOrderBookSnapshot(String tradingPair) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.SNAPSHOT_PATH_URL)
                .params(Map.of("symbol", exchangeSymbol, "limit", 1000))
                .build();
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(request);
        return BinanceOrderBook.snapshotMessageFromExchange(body, Instant.now(), tradingPair);
    }

    public OrderBookMessageStream subscribe(String tradingPair) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.computeIfAbsent(tradingPair, k -> ConcurrentHashMap.newKeySet());
        boolean firstForPair = streams.isEmpty();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        streams.add(stream);
        if (firstForPair) {
            sendSubscribe(tradingPair);
            startSnapshotScheduler(tradingPair);
        }
        return stream;
    }

    public void unsubscribe(OrderBookMessageStream stream) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.get(stream.tradingPair);
        if(streams == null) return;
        streams.remove(stream);
        if (streams.isEmpty()) {
            orderBookMessageStreams.remove(stream.tradingPair);
            sendUnsubscribe(tradingPair);
        }
    }

    private void parse

    private void subscribeChannels(WsConnection wsConnection, List<String> tradingPairs) {
        List<String> tradeParams = new ArrayList<>();
        List<String> depthParams = new ArrayList<>();

        for (String tradingPair : tradingPairs) {
            String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair).toLowerCase();
            tradeParams.add(symbol + "@trade");
            depthParams.add(symbol + "@depth@100ms");
        }
        wsConnection.send(
                new WsRequest(Map.of(
                        "method", "SUBSCRIBE",
                        "params", tradeParams,
                        "id", 1
                ), false)
        );
        wsConnection.send(
                new WsRequest(Map.of(
                        "method", "SUBSCRIBE",
                        "params", depthParams,
                        "id", 2
                ), false)
        );
    }

    private void subscribeTradingPair(String tradingPair) {
        if()

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
                .customWeight(BinanceApiSpec.getTickerPriceChangeDynamicWeight(symbols.size()))
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
