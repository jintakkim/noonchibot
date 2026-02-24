package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.OrderBookMessageStream;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;

@RequiredArgsConstructor
public class BinanceOrderBookDataSource implements OrderBookDataSource {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final Map<String, Set<OrderBookMessageStream>> orderBookMessageStreams = new HashMap<>();
    private WebSocketSession session;


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
        ensureConnected();
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.computeIfAbsent(tradingPair, k -> new HashSet<>());
        boolean firstForPair = streams.isEmpty();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair, () -> unsubscribe(tradingPair, stream));
        streams.add(stream);
        if (firstForPair) {
            sendSubscribe(tradingPair);
            startSnapshotScheduler(tradingPair);
        }
        return stream;
    }

    private void unsubscribe(String tradingPair, OrderBookMessageStream stream) {
        Set<OrderBookMessageStream> streams = orderBookMessageStreams.get(tradingPair);
        if (stream == null) return;
        streams.remove(stream);
        if (streams.isEmpty()) {
            orderBookMessageStreams.remove(tradingPair);
            sendUnsubscribe(tradingPair);
        }
    }

    private void ensureConnected() {
        if (session != null && session.isOpen()) return;

        StandardWebSocketClient client = new StandardWebSocketClient();
        try {
            session = client.execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession s, TextMessage msg) {
                    onWebSocketMessage(msg.getPayload());
                }

                @Override
                public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    handleReconnect();
                }
            }, getWebSocketUrl()).get();
        } catch (Exception e) {
            throw new RuntimeException("WebSocket connection failed", e);
        }
    }

    @Override
    public void listenToOrderBookDiffs(BlockingQueue<OrderBookMessage.DiffMessage> queue) {

    }

    @Override
    public void listenToOrderBookSnapshots(BlockingQueue<OrderBookMessage.SnapshotMessage> queue) {

    }

    @Override
    public void listenToTrades(BlockingQueue<OrderBookMessage.TradeMessage> queue) {

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
