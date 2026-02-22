package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public interface OrderBookDataSource {
    OrderBook getNewOrderBook(String tradingPair);

    void listenToOrderBookDiffs(BlockingQueue<OrderBookMessage.DiffMessage> queue);

    void listenToOrderBookSnapshots(BlockingQueue<OrderBookMessage.SnapshotMessage> queue);

    void listenToTrades(BlockingQueue<OrderBookMessage.TradeMessage> queue);

    boolean subscribeToTradingPair(String tradingPair);

    boolean unsubscribeFromTradingPair(String tradingPair);

    Map<String, BigDecimal> getLastTradedPrices(Set<String> tradingPairs);

    BigDecimal getLastTradedPrice(String tradingPair);
}