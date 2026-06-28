package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlan;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureReconciler;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureSnapshot;
import com.hotak.noonchibot.core.strategy.api.VenueStrategy;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyDecision;
import com.hotak.noonchibot.core.strategy.model.VenueTargetPosition;

import java.util.List;

public class VenueStrategyEngine {
    private final List<VenueStrategy> strategies;
    private final VenueExposureCalculator exposureCalculator;
    private final VenueExposureReconciler exposureReconciler;

    public VenueStrategyEngine(
            List<VenueStrategy> strategies,
            VenueExposureCalculator exposureCalculator,
            VenueExposureReconciler exposureReconciler
    ) {
        this.strategies = List.copyOf(strategies);
        this.exposureCalculator = exposureCalculator;
        this.exposureReconciler = exposureReconciler;
    }

    public VenueExecutionPlan onTick(VenueStrategyContext context) {
        VenueExecutionPlan plan = VenueExecutionPlan.empty();
        for (VenueStrategy strategy : strategies) {
            VenueStrategyDecision decision = strategy.onTick(context);
            plan = plan.merge(toExecutionPlan(strategy, decision, context));
        }
        return plan;
    }

    private VenueExecutionPlan toExecutionPlan(
            VenueStrategy strategy,
            VenueStrategyDecision decision,
            VenueStrategyContext context
    ) {
        if (decision instanceof VenueStrategyDecision.Noop) {
            return VenueExecutionPlan.empty();
        }

        VenueStrategyDecision.Targets targets = (VenueStrategyDecision.Targets) decision;
        VenueExecutionPlan plan = VenueExecutionPlan.empty();
        for (VenueTargetPosition target : targets.positions()) {
            if (!strategy.id().equals(target.strategyId())) {
                throw new IllegalArgumentException("target strategyId does not match strategy id");
            }
            VenueExposureSnapshot exposure = exposureCalculator.calculate(
                    target.strategyId(),
                    target.exchange(),
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
