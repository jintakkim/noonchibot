package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.orderbook.OrderBookMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class OrderBookMessageStream {
    public final String tradingPair;
    private final BlockingQueue<OrderBookMessage.DiffMessage> diffs = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderBookMessage.SnapshotMessage> snapshots = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderBookMessage.TradeMessage> trades = new LinkedBlockingQueue<>();

    public OrderBookMessageStream(String tradingPair) {
        this.tradingPair = tradingPair;
    }

    public OrderBookMessage.DiffMessage takeDiffMessage() throws InterruptedException {
        return diffs.take();
    }

    public OrderBookMessage.SnapshotMessage takeSnapshotMessage() throws InterruptedException {
        return snapshots.take();
    }

    public OrderBookMessage.TradeMessage takeTradeMessage() throws InterruptedException {
        return trades.take();
    }

    public void dispatch(OrderBookMessage message) {
        if (message instanceof OrderBookMessage.DiffMessage m) {
            diffs.add(m);
        } else if (message instanceof OrderBookMessage.SnapshotMessage m) {
            snapshots.add(m);
        } else if (message instanceof OrderBookMessage.TradeMessage m) {
            trades.add(m);
        }
    }
}
