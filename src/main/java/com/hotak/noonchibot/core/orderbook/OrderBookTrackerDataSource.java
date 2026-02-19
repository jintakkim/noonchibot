package com.hotak.noonchibot.core.orderbook;

import java.util.function.Consumer;

public interface OrderBookTrackerDataSource {
    OrderBook getNewOrderBook(String pair);

    void listenToOrderBookDiffs(Consumer<OrderBookMessage> callback);

    void listenToOrderBookSnapshots(Consumer<OrderBookMessage> callback);

    void listenToTrades(Consumer<OrderBookMessage> callback);

    boolean subscribeToTradingPair(String pair);

    boolean unsubscribeFromTradingPair(String pair);
}