package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;

public interface EntryCondition {
    boolean canEnter(VenueStrategyContext context);
}
