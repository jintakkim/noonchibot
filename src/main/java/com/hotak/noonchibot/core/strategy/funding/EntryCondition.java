package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.api.StrategyContext;

public interface EntryCondition {
    boolean canEnter(StrategyContext context);
}
