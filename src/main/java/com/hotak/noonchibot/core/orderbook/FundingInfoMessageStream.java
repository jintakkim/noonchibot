package com.hotak.noonchibot.core.orderbook;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RequiredArgsConstructor
public class FundingInfoMessageStream {
    @Getter
    private final Set<String> subscribedTradingPairs;
    private final BlockingQueue<FundingInfoMessage> delegate = new LinkedBlockingQueue<>();

    public FundingInfoMessage take() throws InterruptedException {
        return delegate.take();
    }

    public FundingInfoMessage poll() {
        return delegate.poll();
    }

    public void add(FundingInfoMessage message) {
        delegate.add(message);
    }
}
