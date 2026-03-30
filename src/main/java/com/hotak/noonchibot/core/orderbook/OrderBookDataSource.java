package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public interface OrderBookDataSource {
    OrderBook getNewOrderBook(String tradingPair);
    OrderBookMessageStream subscribeOrderBookStream(String tradingPair);
    void unsubscribe(OrderBookMessageStream stream);
    Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs);
    BigDecimal getLastTradedPrice(String tradingPair);
}