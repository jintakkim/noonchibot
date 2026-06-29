package com.hotak.noonchibot.core.strategy.arbitrage.condition;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageEvaluation;

import java.math.BigDecimal;
import java.util.Objects;

public record MaxPositionGapCondition(BigDecimal maximumGap) implements StrategyCondition<ArbitrageEvaluation> {
    public MaxPositionGapCondition {
        Objects.requireNonNull(maximumGap, "maximumGap");
        if (maximumGap.signum() < 0) {
            throw new IllegalArgumentException("maximumGap must not be negative");
        }
    }

    @Override
    public boolean matches(ArbitrageEvaluation evaluation) {
        BigDecimal longAmount = evaluation.longExposure().filledBaseAmount().abs();
        BigDecimal shortAmount = evaluation.shortExposure().filledBaseAmount().abs();
        return longAmount.subtract(shortAmount).abs().compareTo(maximumGap) <= 0;
    }
}
