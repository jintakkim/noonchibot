package com.hotak.noonchibot.core.orderbook;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class OrderBookMessageStream {
    public final String tradingPair;
    private final BlockingQueue<OrderBookMessage> delegate = new LinkedBlockingQueue<>();

    public OrderBookMessageStream(String tradingPair) {
        this.tradingPair = tradingPair;
    }

    public OrderBookMessage take() throws InterruptedException {
        return delegate.take();
    }

    public void add(OrderBookMessage message) {
        delegate.add(message);
    }
}
