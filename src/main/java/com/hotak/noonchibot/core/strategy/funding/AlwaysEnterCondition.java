package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;

public class AlwaysEnterCondition implements EntryCondition {
    @Override
    public boolean canEnter(VenueStrategyContext context) {
        return true;
    }
}
