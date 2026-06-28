package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;

import java.time.Instant;
import java.util.Objects;

public record VenueStrategyContext(
        Instant now,
        VenueStrategyMarketView marketView,
        VenueStrategyAccountView accountView,
        VenueStrategyOrderView orderView,
        VenueStrategyPositionView positionView,
        StrategySnapshotSink snapshotSink
) {
    public VenueStrategyContext {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(marketView, "marketView");
        Objects.requireNonNull(accountView, "accountView");
        Objects.requireNonNull(orderView, "orderView");
        Objects.requireNonNull(positionView, "positionView");
        snapshotSink = snapshotSink == null ? StrategySnapshotSink.NOOP : snapshotSink;
    }
}
