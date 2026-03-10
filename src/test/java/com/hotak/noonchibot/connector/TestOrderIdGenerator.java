package com.hotak.noonchibot.connector;

import java.util.concurrent.atomic.AtomicLong;

public class TestOrderIdGenerator implements OrderIdGenerator {
    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public String createClientOrderId(boolean isBuy, String tradingPair, String prefix, int lengthLimit) {
        String side = isBuy ? "BUY" : "SELL";
        return side + "-" + tradingPair + "-" + counter.incrementAndGet();
    }
}
