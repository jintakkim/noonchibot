package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public interface OrderBookTrackerDataSource {
    OrderBook getNewOrderBook(String pair);

    void listenToOrderBookDiffs(BlockingQueue<OrderBookMessage.DiffMessage> queue);

    void listenToOrderBookSnapshots(BlockingQueue<OrderBookMessage.SnapshotMessage> queue);

    void listenToTrades(BlockingQueue<OrderBookMessage.TradeMessage> queue);

    boolean subscribeToTradingPair(String pair);

    boolean unsubscribeFromTradingPair(String pair);

    Map<String, BigDecimal> getLastTradedPrices(Set<String> outdatedPairs, String domain);
}