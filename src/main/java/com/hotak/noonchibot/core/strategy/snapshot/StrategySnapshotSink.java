package com.hotak.noonchibot.core.strategy.snapshot;

public interface StrategySnapshotSink {
    StrategySnapshotSink NOOP = snapshot -> {
    };

    void publish(StrategySnapshot snapshot);
}
