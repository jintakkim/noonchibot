package com.hotak.noonchibot.core.orderbook;

public interface OrderBookDataSource {
    OrderBook getNewOrderBook(String tradingPair);
    OrderBookMessageStream subscribeOrderBookStream(String tradingPair);
    void unsubscribe(OrderBookMessageStream stream);
}