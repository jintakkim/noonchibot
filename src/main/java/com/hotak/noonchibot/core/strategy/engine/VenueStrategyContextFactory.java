package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;

import java.time.Instant;

public interface VenueStrategyContextFactory {
    VenueStrategyContext create(Instant timestamp);
}
