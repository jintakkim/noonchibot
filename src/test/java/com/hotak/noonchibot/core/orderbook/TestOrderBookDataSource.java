package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TestOrderBookDataSource implements OrderBookDataSource {

    private final Map<String, BigDecimal> lastTradedPrices = new ConcurrentHashMap<>();
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Set<OrderBookMessageStream>> streams = new ConcurrentHashMap<>();

    @Override
    public OrderBook getNewOrderBook(String tradingPair) {
        return orderBooks.computeIfAbsent(tradingPair, k -> new OrderBook(false));
    }

    @Override
    public OrderBookMessageStream subscribeOrderBookStream(String tradingPair) {
        Set<OrderBookMessageStream> pairStreams = streams.computeIfAbsent(tradingPair, k -> ConcurrentHashMap.newKeySet());
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        pairStreams.add(stream);
        return stream;
    }

    @Override
    public void unsubscribe(OrderBookMessageStream stream) {
        Set<OrderBookMessageStream> pairStreams = streams.get(stream.tradingPair);
        if (pairStreams != null) pairStreams.remove(stream);
    }

    @Override
    public BigDecimal getLastTradedPrice(String tradingPair) {
        return lastTradedPrices.getOrDefault(tradingPair, BigDecimal.ZERO);
    }

    @Override
    public Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (String pair : tradingPairs) {
            result.put(pair, getLastTradedPrice(pair));
        }
        return result;
    }

    // 테스트 헬퍼

    public void setLastTradedPrice(String tradingPair, BigDecimal price) {
        lastTradedPrices.put(tradingPair, price);
    }

    public void emitMessage(String tradingPair, OrderBookMessage message) {
        Set<OrderBookMessageStream> pairStreams = streams.get(tradingPair);
        if (pairStreams != null) {
            pairStreams.forEach(stream -> stream.add(message));
        }
    }
}