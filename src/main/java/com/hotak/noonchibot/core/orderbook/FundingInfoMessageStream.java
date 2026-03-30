package com.hotak.noonchibot.core.orderbook;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FundingInfoMessageStream {
    public final String tradingPair;
    private final BlockingQueue<FundingInfoMessage> delegate = new LinkedBlockingQueue<>();

    public FundingInfoMessageStream(String tradingPair) {
        this.tradingPair = tradingPair;
    }

    public FundingInfoMessage take() throws InterruptedException {
        return delegate.take();
    }

    public void add(FundingInfoMessage message) {
        delegate.add(message);
    }
}
