package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.api.StrategyOrderView;
import com.hotak.noonchibot.core.strategy.api.StrategyPositionView;
import com.hotak.noonchibot.core.strategy.safety.TradingStateView;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DefaultStrategyContextFactoryTest {
    @Test
    void create_buildsContextFromConfiguredViews() {
        StrategyMarketView marketView = mock(StrategyMarketView.class);
        StrategyAccountView accountView = mock(StrategyAccountView.class);
        StrategyOrderView orderView = mock(StrategyOrderView.class);
        StrategyPositionView positionView = mock(StrategyPositionView.class);
        StrategySnapshotSink snapshotSink = mock(StrategySnapshotSink.class);
        TradingStateView tradingStateView = TradingStateView.RUNNING;
        DefaultStrategyContextFactory factory = new DefaultStrategyContextFactory(
                marketView,
                accountView,
                orderView,
                positionView,
                snapshotSink,
                tradingStateView
        );
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");

        var context = factory.create(timestamp);

        assertThat(context.now()).isEqualTo(timestamp);
        assertThat(context.marketView()).isSameAs(marketView);
        assertThat(context.accountView()).isSameAs(accountView);
        assertThat(context.orderView()).isSameAs(orderView);
        assertThat(context.positionView()).isSameAs(positionView);
        assertThat(context.snapshotSink()).isSameAs(snapshotSink);
        assertThat(context.tradingStateView()).isSameAs(tradingStateView);
    }
}
