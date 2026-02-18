package com.hotak.noonchibot.core.orderbook;

import java.util.Map;

public interface OrderBookTracker {
    Map<String, OrderBook> getOrderBooks();
    Map<String, ReadOnlyOrderBook> getReadOnlyOrderBooks();
    boolean isReady();
    void start();
    void stop();
}
