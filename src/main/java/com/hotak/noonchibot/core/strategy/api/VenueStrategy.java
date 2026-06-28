package com.hotak.noonchibot.core.strategy.api;

public interface VenueStrategy {
    String id();

    VenueStrategyDecision onTick(VenueStrategyContext context);
}
