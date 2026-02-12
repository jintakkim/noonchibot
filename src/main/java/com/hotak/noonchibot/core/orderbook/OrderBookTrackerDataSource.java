package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.OrderBookMessage;

import java.util.concurrent.BlockingQueue;

public interface OrderBookTrackerDataSource {
    OrderBook getNewOrderBook(String pair);

    void listenForOrderBookDiffs(BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage> output);

    void listenForOrderBookSnapshots(BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage> output);

    void listenForTrades(BlockingQueue<OrderBookMessage> output);
}