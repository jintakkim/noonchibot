package com.hotak.noonchibot.core.orderbook;

import java.util.function.Consumer;

public interface OrderBookTrackerDataSource {
    OrderBook getNewOrderBook(String pair);

    void processOrderBookDiffs(Consumer<OrderBookMessage> callback);

    void processOrderBookSnapshots(Consumer<OrderBookMessage> callback);

    void processTrades(Consumer<OrderBookMessage> callback);

    boolean subscribeToTradingPair(String pair);

    boolean unsubscribeFromTradingPair(String pair);
}