package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlanExecutor;
import com.hotak.noonchibot.core.strategy.risk.RiskGate;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.Strategy;
import com.hotak.noonchibot.core.strategy.safety.TradingStateView;

import com.hotak.noonchibot.core.TimeIterator;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
public class StrategyRunner extends TimeIterator {
    private final Strategy strategy;
    private final StrategyContextFactory contextFactory;
    private final TradingStateView tradingStateView;
    private final RiskGate riskGate;
    private final ExecutionPlanExecutor planExecutor;

    public StrategyRunner(
            Strategy strategy,
            StrategyContextFactory contextFactory,
            ExecutionPlanExecutor planExecutor
    ) {
        this(strategy, contextFactory, null, RiskGate.PASS_THROUGH, planExecutor);
    }

    public StrategyRunner(
            Strategy strategy,
            StrategyContextFactory contextFactory,
            RiskGate riskGate,
            ExecutionPlanExecutor planExecutor
    ) {
        this(strategy, contextFactory, null, riskGate, planExecutor);
    }

    public StrategyRunner(
            Strategy strategy,
            StrategyContextFactory contextFactory,
            TradingStateView tradingStateView,
            RiskGate riskGate,
            ExecutionPlanExecutor planExecutor
    ) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.tradingStateView = tradingStateView;
        this.riskGate = Objects.requireNonNull(riskGate, "riskGate");
        this.planExecutor = Objects.requireNonNull(planExecutor, "planExecutor");
    }

    @Override
    public void onTick(Instant timestamp) {
        super.onTick(timestamp);
        StrategyContext context = contextFactory.create(timestamp);
        if (tradingStateView != null) {
            context = context.withTradingStateView(tradingStateView);
        }
        ExecutionPlan plan = strategy.onTick(context);
        ExecutionPlan approvedPlan = riskGate.approve(plan, context);
        if (!approvedPlan.isEmpty()) {
            planExecutor.execute(approvedPlan);
        }
    }
}
