package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.api.StrategyContext;

public class AlwaysEnterCondition implements EntryCondition {
    @Override
    public boolean canEnter(StrategyContext context) {
        return true;
    }
}
