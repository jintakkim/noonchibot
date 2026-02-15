package com.hotak.noonchibot.core.orderbook;

import java.util.function.Consumer;

public interface OrderBookTrackerDataSource {
    OrderBook getNewOrderBook(String pair);

    void listenForOrderBookDiffs(Consumer<OrderBookMessage> callback);

    void listenForOrderBookSnapshots(Consumer<OrderBookMessage> callback);

    void listenForTrades(Consumer<OrderBookMessage> callback);

    boolean subscribeToTradingPair(String pair);

    boolean unsubscribeFromTradingPair(String pair);
}