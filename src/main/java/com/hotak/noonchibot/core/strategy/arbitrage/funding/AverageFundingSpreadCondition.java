package com.hotak.noonchibot.core.strategy.arbitrage.funding;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageEvaluation;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitragePair;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record AverageFundingSpreadCondition(
        Duration window,
        BigDecimal minimumSpread
) implements StrategyCondition<ArbitrageEvaluation> {
    public AverageFundingSpreadCondition {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(minimumSpread, "minimumSpread");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    @Override
    public boolean matches(ArbitrageEvaluation evaluation) {
        ArbitragePair pair = evaluation.pair();
        var market = evaluation.context().marketView();
        BigDecimal longRate = Objects.requireNonNull(market.averageFundingRate(
                pair.longLeg().exchange(),
                pair.longLeg().tradingPair(),
                window
        ), "long average funding rate");
        BigDecimal shortRate = Objects.requireNonNull(market.averageFundingRate(
                pair.shortLeg().exchange(),
                pair.shortLeg().tradingPair(),
                window
        ), "short average funding rate");
        return shortRate.subtract(longRate).compareTo(minimumSpread) >= 0;
    }
}
