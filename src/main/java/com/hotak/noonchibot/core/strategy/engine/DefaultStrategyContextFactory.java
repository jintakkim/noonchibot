package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.api.StrategyOrderView;
import com.hotak.noonchibot.core.strategy.api.StrategyPositionView;
import com.hotak.noonchibot.core.strategy.safety.TradingStateView;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;

import java.time.Instant;
import java.util.Objects;

public class DefaultStrategyContextFactory implements StrategyContextFactory {
    private final StrategyMarketView marketView;
    private final StrategyAccountView accountView;
    private final StrategyOrderView orderView;
    private final StrategyPositionView positionView;
    private final StrategySnapshotSink snapshotSink;
    private final TradingStateView tradingStateView;

    public DefaultStrategyContextFactory(
            StrategyMarketView marketView,
            StrategyAccountView accountView,
            StrategyOrderView orderView,
            StrategyPositionView positionView,
            StrategySnapshotSink snapshotSink,
            TradingStateView tradingStateView
    ) {
        this.marketView = Objects.requireNonNull(marketView, "marketView");
        this.accountView = Objects.requireNonNull(accountView, "accountView");
        this.orderView = Objects.requireNonNull(orderView, "orderView");
        this.positionView = Objects.requireNonNull(positionView, "positionView");
        this.snapshotSink = snapshotSink == null ? StrategySnapshotSink.NOOP : snapshotSink;
        this.tradingStateView = tradingStateView == null ? TradingStateView.RUNNING : tradingStateView;
    }

    @Override
    public StrategyContext create(Instant timestamp) {
        return new StrategyContext(
                timestamp,
                marketView,
                accountView,
                orderView,
                positionView,
                snapshotSink,
                tradingStateView
        );
    }
}
