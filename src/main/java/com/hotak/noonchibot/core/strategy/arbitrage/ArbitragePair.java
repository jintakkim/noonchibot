package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.arbitrage.condition.MaxPositionGapCondition;

import java.math.BigDecimal;
import java.util.Objects;

public record ArbitragePair(
        String pairId,
        ArbitrageLeg longLeg,
        ArbitrageLeg shortLeg,
        BigDecimal totalBaseAmount,
        BigDecimal sliceBaseAmount,
        StrategyCondition<ArbitrageEvaluation> acceptanceCondition,
        StrategyCondition<ArbitrageEvaluation> entryCondition,
        StrategyCondition<ArbitrageEvaluation> exitCondition,
        StrategyCondition<ArbitrageEvaluation> stopLossCondition,
        UnbalancedLegHandling unbalancedLegHandling
) {
    public ArbitragePair(
            String pairId,
            ArbitrageLeg longLeg,
            ArbitrageLeg shortLeg,
            BigDecimal totalBaseAmount,
            BigDecimal sliceBaseAmount,
            BigDecimal acceptableGap,
            StrategyCondition<ArbitrageEvaluation> entryCondition,
            StrategyCondition<ArbitrageEvaluation> exitCondition,
            UnbalancedLegHandling unbalancedLegHandling
    ) {
        this(
                pairId,
                longLeg,
                shortLeg,
                totalBaseAmount,
                sliceBaseAmount,
                new MaxPositionGapCondition(acceptableGap),
                entryCondition,
                exitCondition,
                StrategyCondition.never(),
                unbalancedLegHandling
        );
    }

    public ArbitragePair {
        Objects.requireNonNull(pairId, "pairId");
        Objects.requireNonNull(longLeg, "longLeg");
        Objects.requireNonNull(shortLeg, "shortLeg");
        Objects.requireNonNull(totalBaseAmount, "totalBaseAmount");
        Objects.requireNonNull(sliceBaseAmount, "sliceBaseAmount");
        if (totalBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalBaseAmount must be positive");
        }
        if (sliceBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("sliceBaseAmount must be positive");
        }
        if (longLeg.positionSide() != PositionSide.LONG) {
            throw new IllegalArgumentException("longLeg positionSide must be LONG");
        }
        if (shortLeg.positionSide() != PositionSide.SHORT) {
            throw new IllegalArgumentException("shortLeg positionSide must be SHORT");
        }
        acceptanceCondition = acceptanceCondition == null ? StrategyCondition.always() : acceptanceCondition;
        entryCondition = entryCondition == null ? StrategyCondition.always() : entryCondition;
        exitCondition = exitCondition == null ? StrategyCondition.never() : exitCondition;
        stopLossCondition = stopLossCondition == null ? StrategyCondition.never() : stopLossCondition;
        unbalancedLegHandling = unbalancedLegHandling == null
                ? UnbalancedLegHandling.WAIT_FOR_OTHER_LEG
                : unbalancedLegHandling;
    }
}
