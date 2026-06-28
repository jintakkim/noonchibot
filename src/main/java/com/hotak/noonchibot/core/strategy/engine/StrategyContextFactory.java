package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.api.StrategyContext;

import java.time.Instant;

public interface StrategyContextFactory {
    StrategyContext create(Instant timestamp);
}
