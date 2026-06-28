package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlanExecutor;
import com.hotak.noonchibot.core.strategy.risk.VenueRiskGate;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;

import com.hotak.noonchibot.core.TimeIterator;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
public class VenueStrategyRunner extends TimeIterator {
    private final VenueStrategyEngine strategyEngine;
    private final VenueStrategyContextFactory contextFactory;
    private final VenueRiskGate riskGate;
    private final VenueExecutionPlanExecutor planExecutor;

    public VenueStrategyRunner(
            VenueStrategyEngine strategyEngine,
            VenueStrategyContextFactory contextFactory,
            VenueExecutionPlanExecutor planExecutor
    ) {
        this(strategyEngine, contextFactory, VenueRiskGate.PASS_THROUGH, planExecutor);
    }

    public VenueStrategyRunner(
            VenueStrategyEngine strategyEngine,
            VenueStrategyContextFactory contextFactory,
            VenueRiskGate riskGate,
            VenueExecutionPlanExecutor planExecutor
    ) {
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate");
        this.planExecutor = Objects.requireNonNull(planExecutor, "planExecutor");
    }

    @Override
    public void onTick(Instant timestamp) {
        super.onTick(timestamp);
        VenueStrategyContext context = contextFactory.create(timestamp);
        VenueExecutionPlan plan = strategyEngine.onTick(context);
        VenueExecutionPlan approvedPlan = riskGate.approve(plan, context);
        if (!approvedPlan.isEmpty()) {
            planExecutor.execute(approvedPlan);
        }
    }
}
