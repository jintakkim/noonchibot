package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.strategy.api.Strategy;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureReconciler;
import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.exposure.ExposureSnapshot;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionUrgency;
import com.hotak.noonchibot.core.strategy.model.ExchangeOrderView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PairArbitrageStrategy implements Strategy {
    private final PairArbitrageConfig config;
    private final ExposureCalculator exposureCalculator;
    private final ExposureReconciler exposureReconciler;

    public PairArbitrageStrategy(
            PairArbitrageConfig config,
            ExposureCalculator exposureCalculator
    ) {
        this(config, exposureCalculator, new ExposureReconciler(ReconcilePolicy.conservative()));
    }

    public PairArbitrageStrategy(
            PairArbitrageConfig config,
            ExposureCalculator exposureCalculator,
            ExposureReconciler exposureReconciler
    ) {
        this.config = config;
        this.exposureCalculator = exposureCalculator;
        this.exposureReconciler = exposureReconciler;
    }

    @Override
    public String id() {
        return config.strategyId();
    }

    @Override
    public ExecutionPlan onTick(StrategyContext context) {
        ExecutionPlan plan = ExecutionPlan.empty();
        for (ArbitragePair pair : config.pairs()) {
            plan = plan.merge(evaluate(pair, context));
        }
        return plan;
    }

    private ExecutionPlan reconcileTargets(
            List<TargetPosition> targets,
            StrategyContext context
    ) {
        ExecutionPlan plan = ExecutionPlan.empty();
        for (TargetPosition target : targets) {
            ExposureSnapshot exposure = calculateExposure(target, context);
            plan = plan.merge(exposureReconciler.reconcile(target, exposure));
        }
        return plan;
    }

    private ExecutionPlan evaluate(
            ArbitragePair pair,
            StrategyContext context
    ) {
        ExposureSnapshot longExposure = calculateExposure(pair.longLeg(), context);
        ExposureSnapshot shortExposure = calculateExposure(pair.shortLeg(), context);
        ArbitrageEvaluation evaluation = new ArbitrageEvaluation(context, pair, longExposure, shortExposure);
        boolean entryMatched = pair.entryCondition().matches(evaluation);
        boolean orderValid = pair.orderValidityCondition().matches(evaluation);
        publishSnapshot(pair, context, longExposure, shortExposure, entryMatched, orderValid);

        if (pair.stopLossCondition().matches(evaluation)) {
            return exitActions(pair, longExposure, shortExposure, context, "stop loss", true);
        }

        if (pair.exitCondition().matches(evaluation)) {
            return exitActions(pair, longExposure, shortExposure, context, "exit", false);
        }

        if (!orderValid) {
            return invalidOrderActions(pair, longExposure, shortExposure, context);
        }

        if (longExposure.hasActiveOrders() || shortExposure.hasActiveOrders()) {
            return ExecutionPlan.empty();
        }

        BigDecimal longAmount = longExposure.filledBaseAmount().abs();
        BigDecimal shortAmount = shortExposure.filledBaseAmount().abs();
        if (longAmount.compareTo(shortAmount) != 0) {
            return reconcileTargets(rebalanceTargets(
                    pair,
                    longAmount,
                    shortAmount,
                    evaluation
            ), context);
        }

        if (!entryMatched) {
            return ExecutionPlan.empty();
        }

        return reconcileTargets(entryTargets(pair, longAmount, shortAmount, context.now()), context);
    }

    private List<TargetPosition> rebalanceTargets(
            ArbitragePair pair,
            BigDecimal longAmount,
            BigDecimal shortAmount,
            ArbitrageEvaluation evaluation
    ) {
        if (longAmount.compareTo(shortAmount) > 0) {
            ArbitrageLeg shortLeg = pair.shortLeg();
            BigDecimal target = moveToward(
                    shortAmount,
                    longAmount,
                    pair.sliceBaseAmount()
            );
            return List.of(toTarget(
                    pair.pairId(),
                    shortLeg,
                    target.negate(),
                    evaluation.context().now(),
                    pair.pairId() + " rebalance short leg"
            ));
        }

        ArbitrageLeg longLeg = pair.longLeg();
        BigDecimal target = moveToward(
                longAmount,
                shortAmount,
                pair.sliceBaseAmount()
        );
        return List.of(toTarget(
                pair.pairId(),
                longLeg,
                target,
                evaluation.context().now(),
                pair.pairId() + " rebalance long leg"
        ));
    }

    private ExecutionPlan invalidOrderActions(
            ArbitragePair pair,
            ExposureSnapshot longExposure,
            ExposureSnapshot shortExposure,
            StrategyContext context
    ) {
        if (longExposure.hasPendingCancelOrders() || shortExposure.hasPendingCancelOrders()) {
            return ExecutionPlan.empty();
        }

        List<ExecutionCommand> cancellations = new ArrayList<>();
        if (longExposure.hasOpenOrders()) {
            cancellations.addAll(cancelCommands(
                    pair.longLeg(),
                    context,
                    pair.pairId() + " order validity lost cancel long orders"
            ));
        }
        if (shortExposure.hasOpenOrders()) {
            cancellations.addAll(cancelCommands(
                    pair.shortLeg(),
                    context,
                    pair.pairId() + " order validity lost cancel short orders"
            ));
        }
        if (!cancellations.isEmpty()) {
            return new ExecutionPlan(cancellations);
        }

        BigDecimal longAmount = longExposure.filledBaseAmount().abs();
        BigDecimal shortAmount = shortExposure.filledBaseAmount().abs();
        if (longAmount.compareTo(shortAmount) == 0) {
            return ExecutionPlan.empty();
        }
        return reconcileTargets(
                reduceLargerLegTargets(pair, longAmount, shortAmount, context.now()),
                context
        );
    }

    private List<TargetPosition> reduceLargerLegTargets(
            ArbitragePair pair,
            BigDecimal longAmount,
            BigDecimal shortAmount,
            Instant now
    ) {
        if (longAmount.compareTo(shortAmount) > 0) {
            BigDecimal target = moveToward(longAmount, shortAmount, pair.sliceBaseAmount());
            return List.of(toTarget(
                    pair.pairId(),
                    pair.longLeg(),
                    target,
                    now,
                    pair.pairId() + " reduce long leg",
                    pair.longLeg().executionPolicy().orderStyle().asReduceOnly(),
                    ExecutionUrgency.NORMAL
            ));
        }
        BigDecimal target = moveToward(shortAmount, longAmount, pair.sliceBaseAmount());
        return List.of(toTarget(
                pair.pairId(),
                pair.shortLeg(),
                target.negate(),
                now,
                pair.pairId() + " reduce short leg",
                pair.shortLeg().executionPolicy().orderStyle().asReduceOnly(),
                ExecutionUrgency.NORMAL
        ));
    }

    private List<TargetPosition> entryTargets(
            ArbitragePair pair,
            BigDecimal longAmount,
            BigDecimal shortAmount,
            Instant now
    ) {
        if (longAmount.compareTo(pair.totalBaseAmount()) >= 0
                && shortAmount.compareTo(pair.totalBaseAmount()) >= 0) {
            return List.of();
        }

        BigDecimal commonAmount = longAmount.min(shortAmount);
        BigDecimal nextAmount = commonAmount.add(pair.sliceBaseAmount()).min(pair.totalBaseAmount());
        List<TargetPosition> targets = new ArrayList<>();
        if (nextAmount.compareTo(longAmount) > 0) {
            targets.add(toTarget(pair.pairId(), pair.longLeg(), nextAmount, now, pair.pairId() + " enter long leg"));
        }
        if (nextAmount.compareTo(shortAmount) > 0) {
            targets.add(toTarget(pair.pairId(), pair.shortLeg(), nextAmount.negate(), now, pair.pairId() + " enter short leg"));
        }
        return targets;
    }

    private ExecutionPlan exitActions(
            ArbitragePair pair,
            ExposureSnapshot longExposure,
            ExposureSnapshot shortExposure,
            StrategyContext context,
            String reason,
            boolean stopLoss
    ) {
        if (longExposure.hasPendingCancelOrders() || shortExposure.hasPendingCancelOrders()) {
            return ExecutionPlan.empty();
        }

        List<ExecutionCommand> cancellations = new ArrayList<>();
        if (longExposure.hasOpenOrders()) {
            cancellations.addAll(cancelCommands(
                    pair.longLeg(),
                    context,
                    pair.pairId() + " " + reason + " cancel long orders"
            ));
        }
        if (shortExposure.hasOpenOrders()) {
            cancellations.addAll(cancelCommands(
                    pair.shortLeg(),
                    context,
                    pair.pairId() + " " + reason + " cancel short orders"
            ));
        }
        if (!cancellations.isEmpty()) {
            return new ExecutionPlan(cancellations);
        }

        List<TargetPosition> targets = new ArrayList<>();
        if (longExposure.filledBaseAmount().signum() != 0 || longExposure.hasActiveOrders()) {
            targets.add(toTarget(
                    pair.pairId(),
                    pair.longLeg(),
                    BigDecimal.ZERO,
                    context.now(),
                    pair.pairId() + " " + reason + " long leg",
                    stopLoss
                            ? TargetOrderStyle.marketReduceOnly()
                            : pair.longLeg().executionPolicy().orderStyle().asReduceOnly(),
                    stopLoss ? ExecutionUrgency.EMERGENCY : ExecutionUrgency.NORMAL
            ));
        }
        if (shortExposure.filledBaseAmount().signum() != 0 || shortExposure.hasActiveOrders()) {
            targets.add(toTarget(
                    pair.pairId(),
                    pair.shortLeg(),
                    BigDecimal.ZERO,
                    context.now(),
                    pair.pairId() + " " + reason + " short leg",
                    stopLoss
                            ? TargetOrderStyle.marketReduceOnly()
                            : pair.shortLeg().executionPolicy().orderStyle().asReduceOnly(),
                    stopLoss ? ExecutionUrgency.EMERGENCY : ExecutionUrgency.NORMAL
            ));
        }
        return reconcileTargets(targets, context);
    }

    private ExposureSnapshot calculateExposure(
            ArbitrageLeg leg,
            StrategyContext context
    ) {
        return exposureCalculator.calculate(
                config.strategyId(),
                leg.exchange(),
                leg.tradingPair(),
                leg.positionSide(),
                context.positionView().positions(),
                context.orderView().openOrders()
        );
    }

    private ExposureSnapshot calculateExposure(
            TargetPosition target,
            StrategyContext context
    ) {
        return exposureCalculator.calculate(
                config.strategyId(),
                target.exchange(),
                target.tradingPair(),
                target.positionSide(),
                context.positionView().positions(),
                context.orderView().openOrders()
        );
    }

    private List<ExecutionCommand> cancelCommands(
            ArbitrageLeg leg,
            StrategyContext context,
            String reason
    ) {
        return context.orderView().openOrders().stream()
                .filter(order -> order.exchange() == leg.exchange())
                .map(ExchangeOrderView::order)
                .filter(order -> order.tradingPair().equals(leg.tradingPair()))
                .filter(order -> !order.state().isTerminal())
                .map(order -> new ExecutionCommand.CancelOrder(
                        config.strategyId(),
                        leg.exchange(),
                        reason,
                        order.clientOrderId()
                ))
                .map(ExecutionCommand.class::cast)
                .toList();
    }

    private TargetPosition toTarget(
            String executionGroupId,
            ArbitrageLeg leg,
            BigDecimal targetBaseAmount,
            Instant now,
            String reason
    ) {
        return toTarget(
                executionGroupId,
                leg,
                targetBaseAmount,
                now,
                reason,
                null,
                ExecutionUrgency.NORMAL
        );
    }

    private TargetPosition toTarget(
            String executionGroupId,
            ArbitrageLeg leg,
            BigDecimal targetBaseAmount,
            Instant now,
            String reason,
            TargetOrderStyle orderStyle,
            ExecutionUrgency urgency
    ) {
        return new TargetPosition(
                config.strategyId(),
                executionGroupId,
                leg.exchange(),
                leg.tradingPair(),
                leg.positionSide(),
                targetBaseAmount,
                orderStyle == null ? leg.executionPolicy().orderStyle() : orderStyle,
                now.plus(leg.executionPolicy().targetTtl()),
                reason,
                urgency
        );
    }

    private BigDecimal moveToward(BigDecimal current, BigDecimal target, BigDecimal maxStep) {
        BigDecimal delta = target.subtract(current);
        if (delta.abs().compareTo(maxStep) <= 0) {
            return target;
        }
        return current.add(maxStep.multiply(BigDecimal.valueOf(delta.signum())));
    }

    private void publishSnapshot(
            ArbitragePair pair,
            StrategyContext context,
            ExposureSnapshot longExposure,
            ExposureSnapshot shortExposure,
            boolean entryMatched,
            boolean orderValid
    ) {
        context.snapshotSink().publish(new StrategySnapshot(
                config.strategyId(),
                context.now(),
                "PAIR_ARBITRAGE",
                Map.of(
                        "pairId", pair.pairId(),
                        "longExchange", pair.longLeg().exchange().getId(),
                        "shortExchange", pair.shortLeg().exchange().getId(),
                        "longBaseAmount", longExposure.filledBaseAmount(),
                        "shortBaseAmount", shortExposure.filledBaseAmount(),
                        "targetBaseAmount", pair.totalBaseAmount(),
                        "sliceBaseAmount", pair.sliceBaseAmount(),
                        "entryMatched", entryMatched,
                        "orderValid", orderValid
                )
        ));
    }

}
