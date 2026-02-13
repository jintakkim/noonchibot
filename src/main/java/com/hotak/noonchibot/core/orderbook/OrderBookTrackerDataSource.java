package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public interface OrderBookTrackerDataSource {
    OrderBook getNewOrderBook(String pair);

    Map<String, BigDecimal> getLastTradedPrices(List<String> pairs, String domain);

    void listenForOrderBookDiffs(BlockingQueue<OrderBookMessage> output);

    void listenForOrderBookSnapshots(BlockingQueue<OrderBookMessage> output);

    void listenForTrades(BlockingQueue<OrderBookMessage> output);

    boolean subscribeToTradingPair(String pair);

    boolean unsubscribeFromTradingPair(String pair);
}