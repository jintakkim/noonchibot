package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;

public interface Strategy {
    String id();
    ExecutionPlan onTick(StrategyContext context);
}
