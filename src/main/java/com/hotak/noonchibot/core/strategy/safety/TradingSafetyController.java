package com.hotak.noonchibot.core.strategy.safety;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TradingSafetyController implements TradingStateView {
    private final AtomicReference<TradingStatus> status = new AtomicReference<>(TradingStatus.RUNNING);

    @Override
    public TradingStatus status() {
        return status.get();
    }

    public void pause(Throwable cause) {
        if (status.getAndSet(TradingStatus.TRADING_PAUSED) == TradingStatus.RUNNING) {
            log.error("trading paused after order failure", cause);
        }
    }

    public void resumeAfterReconciliation() {
        status.set(TradingStatus.RUNNING);
        log.info("trading resumed manually");
    }

}
