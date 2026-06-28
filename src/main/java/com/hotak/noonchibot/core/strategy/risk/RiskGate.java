package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;

public interface RiskGate {
    RiskGate PASS_THROUGH = (plan, context) -> plan;

    ExecutionPlan approve(ExecutionPlan plan, StrategyContext context);
}
