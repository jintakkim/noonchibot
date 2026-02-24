package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.core.orderbook.AbstractOrderBookTest;
import com.hotak.noonchibot.core.orderbook.OrderBook;


public class BinanceOrderBookTest extends AbstractOrderBookTest {
    @Override
    protected boolean isDex() {
        return false;
    }

    @Override
    public OrderBook createOrderBook() {
        return new BinanceOrderBook();
    }
}
