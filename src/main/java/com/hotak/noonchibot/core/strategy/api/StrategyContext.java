package com.hotak.noonchibot.core.strategy.api;

import java.time.Instant;

public record StrategyContext(
        Instant now,
        StrategyMarketView marketView,
        StrategyAccountView accountView,
        StrategyOrderView orderView,
        StrategyPositionView positionView
) {
}
