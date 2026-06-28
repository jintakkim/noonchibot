package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlanExecutor;
import com.hotak.noonchibot.core.strategy.risk.RiskGate;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;

import com.hotak.noonchibot.core.TimeIterator;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
public class StrategyRunner extends TimeIterator {
    private final StrategyEngine strategyEngine;
    private final StrategyContextFactory contextFactory;
    private final RiskGate riskGate;
    private final ExecutionPlanExecutor planExecutor;

    public StrategyRunner(
            StrategyEngine strategyEngine,
            StrategyContextFactory contextFactory,
            ExecutionPlanExecutor planExecutor
    ) {
        this(strategyEngine, contextFactory, RiskGate.PASS_THROUGH, planExecutor);
    }

    public StrategyRunner(
            StrategyEngine strategyEngine,
            StrategyContextFactory contextFactory,
            RiskGate riskGate,
            ExecutionPlanExecutor planExecutor
    ) {
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate");
        this.planExecutor = Objects.requireNonNull(planExecutor, "planExecutor");
    }

    @Override
    public void onTick(Instant timestamp) {
        super.onTick(timestamp);
        StrategyContext context = contextFactory.create(timestamp);
        ExecutionPlan plan = strategyEngine.onTick(context);
        ExecutionPlan approvedPlan = riskGate.approve(plan, context);
        if (!approvedPlan.isEmpty()) {
            planExecutor.execute(approvedPlan);
        }
    }
}
