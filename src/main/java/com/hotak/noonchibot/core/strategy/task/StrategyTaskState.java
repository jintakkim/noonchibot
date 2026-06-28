package com.hotak.noonchibot.core.strategy.task;

public enum StrategyTaskState {
    PLANNED,
    SUBMITTING,
    WAITING_FILL,
    PARTIALLY_FILLED,
    CANCELING_STALE_ORDER,
    HEDGING_TAKER,
    REDUCING_EXPOSURE,
    CLOSING,
    COMPLETED,
    FAILED,
    EXPIRED
}
