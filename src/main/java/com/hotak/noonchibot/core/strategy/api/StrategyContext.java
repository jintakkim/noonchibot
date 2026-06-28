package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;

import java.time.Instant;
import java.util.Objects;

public record StrategyContext(
        Instant now,
        StrategyMarketView marketView,
        StrategyAccountView accountView,
        StrategyOrderView orderView,
        StrategyPositionView positionView,
        StrategySnapshotSink snapshotSink
) {
    public StrategyContext {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(marketView, "marketView");
        Objects.requireNonNull(accountView, "accountView");
        Objects.requireNonNull(orderView, "orderView");
        Objects.requireNonNull(positionView, "positionView");
        snapshotSink = snapshotSink == null ? StrategySnapshotSink.NOOP : snapshotSink;
    }
}
