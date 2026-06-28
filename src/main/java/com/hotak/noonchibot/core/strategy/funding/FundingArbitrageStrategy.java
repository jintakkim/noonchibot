package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureSnapshot;
import com.hotak.noonchibot.core.strategy.api.VenueStrategy;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyDecision;
import com.hotak.noonchibot.core.strategy.model.VenueTargetPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class FundingArbitrageStrategy implements VenueStrategy {
    private final FundingArbitrageConfig config;
    private final VenueExposureCalculator exposureCalculator;

    public FundingArbitrageStrategy(
            FundingArbitrageConfig config,
            VenueExposureCalculator exposureCalculator
    ) {
        this.config = config;
        this.exposureCalculator = exposureCalculator;
    }

    @Override
    public String id() {
        return config.strategyId();
    }

    @Override
    public VenueStrategyDecision onTick(VenueStrategyContext context) {
        FundingArbitrageLeg longLeg = config.longLeg();
        FundingArbitrageLeg shortLeg = config.shortLeg();
        VenueExposureSnapshot longExposure = calculateExposure(longLeg, context);
        VenueExposureSnapshot shortExposure = calculateExposure(shortLeg, context);

        publishSnapshot(context, longExposure, shortExposure);

        if (!config.entryCondition().canEnter(context)) {
            return new VenueStrategyDecision.Noop("entry condition rejected");
        }
        if (!longLeg.legCondition().canEnter(context) || !shortLeg.legCondition().canEnter(context)) {
            return new VenueStrategyDecision.Noop("leg condition rejected");
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
            return new VenueStrategyDecision.Noop("target exposure reached");
        }

        Instant now = context.now();
        return new VenueStrategyDecision.Targets(List.of(
                toTarget(longLeg, nextLongTarget, now, "funding arbitrage long leg"),
                toTarget(shortLeg, nextShortTarget, now, "funding arbitrage short leg")
        ));
    }

    private VenueExposureSnapshot calculateExposure(
            FundingArbitrageLeg leg,
            VenueStrategyContext context
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

    private VenueTargetPosition toTarget(
            FundingArbitrageLeg leg,
            BigDecimal targetBaseAmount,
            Instant now,
            String reason
    ) {
        return new VenueTargetPosition(
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
            VenueStrategyContext context,
            VenueExposureSnapshot longExposure,
            VenueExposureSnapshot shortExposure
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
