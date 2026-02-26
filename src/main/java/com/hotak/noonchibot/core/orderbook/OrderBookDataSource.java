package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.OrderBookMessageStream;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public interface OrderBookDataSource {
    OrderBook getNewOrderBook(String tradingPair);
    OrderBookMessageStream subscribe(String tradingPair);
    void unsubscribe(OrderBookMessageStream stream);
    Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs);
    BigDecimal getLastTradedPrice(String tradingPair);
}