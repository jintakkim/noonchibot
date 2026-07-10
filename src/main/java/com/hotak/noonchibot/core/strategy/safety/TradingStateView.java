package com.hotak.noonchibot.core.strategy.safety;

@FunctionalInterface
public interface TradingStateView {
    TradingStateView RUNNING = () -> TradingStatus.RUNNING;

    TradingStatus status();

    default boolean isPaused() {
        return status() == TradingStatus.TRADING_PAUSED;
    }
}
