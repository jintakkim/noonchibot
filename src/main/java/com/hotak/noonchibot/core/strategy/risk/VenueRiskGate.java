package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlan;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;

public interface VenueRiskGate {
    VenueRiskGate PASS_THROUGH = (plan, context) -> plan;

    VenueExecutionPlan approve(VenueExecutionPlan plan, VenueStrategyContext context);
}
