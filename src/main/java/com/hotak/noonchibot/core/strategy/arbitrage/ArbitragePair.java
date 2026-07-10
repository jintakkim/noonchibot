package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.strategy.api.StrategyCondition;

import java.math.BigDecimal;
import java.util.Objects;

public record ArbitragePair(
        String pairId,
        ArbitrageLeg longLeg,
        ArbitrageLeg shortLeg,
        BigDecimal totalBaseAmount,
        BigDecimal sliceBaseAmount,
        StrategyCondition<ArbitrageEvaluation> orderValidityCondition,
        StrategyCondition<ArbitrageEvaluation> entryCondition,
        StrategyCondition<ArbitrageEvaluation> exitCondition,
        StrategyCondition<ArbitrageEvaluation> stopLossCondition
) {
    public ArbitragePair(
            String pairId,
            ArbitrageLeg longLeg,
            ArbitrageLeg shortLeg,
            BigDecimal totalBaseAmount,
            BigDecimal sliceBaseAmount,
            StrategyCondition<ArbitrageEvaluation> orderValidityCondition,
            StrategyCondition<ArbitrageEvaluation> entryCondition,
            StrategyCondition<ArbitrageEvaluation> exitCondition
    ) {
        this(
                pairId,
                longLeg,
                shortLeg,
                totalBaseAmount,
                sliceBaseAmount,
                orderValidityCondition,
                entryCondition,
                exitCondition,
                StrategyCondition.never()
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
        entryCondition = entryCondition == null ? StrategyCondition.always() : entryCondition;
        orderValidityCondition = orderValidityCondition == null ? entryCondition : orderValidityCondition;
        exitCondition = exitCondition == null ? StrategyCondition.never() : exitCondition;
        stopLossCondition = stopLossCondition == null ? StrategyCondition.never() : stopLossCondition;
    }
}
