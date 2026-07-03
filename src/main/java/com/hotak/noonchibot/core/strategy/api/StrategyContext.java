package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.strategy.safety.TradingStateView;

import java.time.Instant;
import java.util.Objects;

public record StrategyContext(
        Instant now,
        StrategyMarketView marketView,
        StrategyAccountView accountView,
        StrategyOrderView orderView,
        StrategyPositionView positionView,
        StrategySnapshotSink snapshotSink,
        TradingStateView tradingStateView
) {
    public StrategyContext(
            Instant now,
            StrategyMarketView marketView,
            StrategyAccountView accountView,
            StrategyOrderView orderView,
            StrategyPositionView positionView,
            StrategySnapshotSink snapshotSink
    ) {
        this(
                now,
                marketView,
                accountView,
                orderView,
                positionView,
                snapshotSink,
                TradingStateView.RUNNING
        );
    }

    public StrategyContext {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(marketView, "marketView");
        Objects.requireNonNull(accountView, "accountView");
        Objects.requireNonNull(orderView, "orderView");
        Objects.requireNonNull(positionView, "positionView");
        snapshotSink = snapshotSink == null ? StrategySnapshotSink.NOOP : snapshotSink;
        tradingStateView = tradingStateView == null ? TradingStateView.RUNNING : tradingStateView;
    }

    public StrategyContext withTradingStateView(TradingStateView tradingStateView) {
        return new StrategyContext(
                now,
                marketView,
                accountView,
                orderView,
                positionView,
                snapshotSink,
                tradingStateView
        );
    }
}
