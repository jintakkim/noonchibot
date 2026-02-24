package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.orderbook.OrderBookMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class OrderBookMessageStream implements AutoCloseable {
    public final String tradingPair;
    public final BlockingQueue<OrderBookMessage.DiffMessage> diffs = new LinkedBlockingQueue<>();
    public final BlockingQueue<OrderBookMessage.SnapshotMessage> snapshots = new LinkedBlockingQueue<>();
    public final BlockingQueue<OrderBookMessage.TradeMessage> trades = new LinkedBlockingQueue<>();
    private final Runnable onClose;

    public OrderBookMessageStream(String tradingPair, Runnable onClose) {
        this.tradingPair = tradingPair;
        this.onClose = onClose;
    }

    void dispatch(OrderBookMessage message) {
        if (message instanceof OrderBookMessage.DiffMessage m) {
            diffs.add(m);
        } else if (message instanceof OrderBookMessage.SnapshotMessage m) {
            snapshots.add(m);
        } else if (message instanceof OrderBookMessage.TradeMessage m) {
            trades.add(m);
        }
    }

    @Override
    public void close() {
        onClose.run();
    }

}
