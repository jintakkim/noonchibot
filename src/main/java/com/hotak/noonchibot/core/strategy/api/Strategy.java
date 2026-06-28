package com.hotak.noonchibot.core.strategy.api;

public interface Strategy {
    String id();

    StrategyDecision onTick(StrategyContext context);
}
