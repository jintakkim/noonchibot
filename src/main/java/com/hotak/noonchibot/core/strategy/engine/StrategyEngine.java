package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureReconciler;
import com.hotak.noonchibot.core.strategy.exposure.ExposureSnapshot;
import com.hotak.noonchibot.core.strategy.api.Strategy;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyDecision;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import java.util.List;

public class StrategyEngine {
    private final List<Strategy> strategies;
    private final ExposureCalculator exposureCalculator;
    private final ExposureReconciler exposureReconciler;

    public StrategyEngine(
            List<Strategy> strategies,
            ExposureCalculator exposureCalculator,
            ExposureReconciler exposureReconciler
    ) {
        this.strategies = List.copyOf(strategies);
        this.exposureCalculator = exposureCalculator;
        this.exposureReconciler = exposureReconciler;
    }

    public ExecutionPlan onTick(StrategyContext context) {
        ExecutionPlan plan = ExecutionPlan.empty();
        for (Strategy strategy : strategies) {
            StrategyDecision decision = strategy.onTick(context);
            plan = plan.merge(toExecutionPlan(strategy, decision, context));
        }
        return plan;
    }

    private ExecutionPlan toExecutionPlan(
            Strategy strategy,
            StrategyDecision decision,
            StrategyContext context
    ) {
        if (decision instanceof StrategyDecision.Noop) {
            return ExecutionPlan.empty();
        }
        StrategyDecision.Targets targets = (StrategyDecision.Targets) decision;
        ExecutionPlan plan = ExecutionPlan.empty();
        for (TargetPosition target : targets.positions()) {
            if (!strategy.id().equals(target.strategyId())) {
                throw new IllegalArgumentException("target strategyId does not match strategy id");
            }
            ExposureSnapshot exposure = exposureCalculator.calculate(
                    target.strategyId(),
                    target.tradingPair(),
                    target.positionSide(),
                    context.positionView().positions(),
                    context.orderView().openOrders()
            );
            plan = plan.merge(exposureReconciler.reconcile(
                    target,
                    exposure,
                    context.orderView().openOrders(),
                    context.now()
            ));
        }
        return plan;
    }
}
