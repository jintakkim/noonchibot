package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureSnapshot;
import com.hotak.noonchibot.core.strategy.api.Strategy;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyDecision;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class FundingArbitrageStrategy implements Strategy {
    private final FundingArbitrageConfig config;
    private final ExposureCalculator exposureCalculator;

    public FundingArbitrageStrategy(
            FundingArbitrageConfig config,
            ExposureCalculator exposureCalculator
    ) {
        this.config = config;
        this.exposureCalculator = exposureCalculator;
    }

    @Override
    public String id() {
        return config.strategyId();
    }

    @Override
    public StrategyDecision onTick(StrategyContext context) {
        FundingArbitrageLeg longLeg = config.longLeg();
        FundingArbitrageLeg shortLeg = config.shortLeg();
        ExposureSnapshot longExposure = calculateExposure(longLeg, context);
        ExposureSnapshot shortExposure = calculateExposure(shortLeg, context);

        publishSnapshot(context, longExposure, shortExposure);

        if (!config.entryCondition().canEnter(context)) {
            return new StrategyDecision.Noop("entry condition rejected");
        }
        if (!longLeg.legCondition().canEnter(context) || !shortLeg.legCondition().canEnter(context)) {
            return new StrategyDecision.Noop("leg condition rejected");
        }

        BigDecimal nextLongTarget = moveToward(
                longExposure.projectedBaseAmount(),
                config.totalBaseAmount(),
                longLeg.executionPolicy().maxSliceBaseAmount()
        );
        BigDecimal nextShortTarget = moveToward(
                shortExposure.projectedBaseAmount(),
                config.totalBaseAmount().negate(),
                shortLeg.executionPolicy().maxSliceBaseAmount()
        );

        if (nextLongTarget.compareTo(longExposure.projectedBaseAmount()) == 0
                && nextShortTarget.compareTo(shortExposure.projectedBaseAmount()) == 0) {
            return new StrategyDecision.Noop("target exposure reached");
        }

        Instant now = context.now();
        return new StrategyDecision.Targets(List.of(
                toTarget(longLeg, nextLongTarget, now, "funding arbitrage long leg"),
                toTarget(shortLeg, nextShortTarget, now, "funding arbitrage short leg")
        ));
    }

    private ExposureSnapshot calculateExposure(
            FundingArbitrageLeg leg,
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

    private TargetPosition toTarget(
            FundingArbitrageLeg leg,
            BigDecimal targetBaseAmount,
            Instant now,
            String reason
    ) {
        return new TargetPosition(
                config.strategyId(),
                leg.exchange(),
                leg.tradingPair(),
                leg.positionSide(),
                targetBaseAmount,
                leg.executionPolicy().orderStyle(),
                now.plus(leg.executionPolicy().targetTtl()),
                reason
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
            StrategyContext context,
            ExposureSnapshot longExposure,
            ExposureSnapshot shortExposure
    ) {
        context.snapshotSink().publish(new StrategySnapshot(
                config.strategyId(),
                context.now(),
                "FUNDING_ARBITRAGE",
                Map.of(
                        "longExchange", config.longLeg().exchange().getId(),
                        "shortExchange", config.shortLeg().exchange().getId(),
                        "longProjectedBaseAmount", longExposure.projectedBaseAmount(),
                        "shortProjectedBaseAmount", shortExposure.projectedBaseAmount(),
                        "targetBaseAmount", config.totalBaseAmount(),
                        "unbalancedLegHandling", config.unbalancedLegHandling().name()
                )
        ));
    }
}
