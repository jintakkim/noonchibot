package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record PairArbitrageConfig(
        String strategyId,
        List<ArbitragePair> pairs
) {
    public PairArbitrageConfig(
            String strategyId,
            ArbitrageLeg longLeg,
            ArbitrageLeg shortLeg,
            BigDecimal totalBaseAmount,
            BigDecimal sliceBaseAmount,
            StrategyCondition<ArbitrageEvaluation> entryCondition,
            UnbalancedLegHandling unbalancedLegHandling
    ) {
        this(
                strategyId,
                List.of(new ArbitragePair(
                        "default",
                        longLeg,
                        shortLeg,
                        totalBaseAmount,
                        sliceBaseAmount,
                        BigDecimal.ZERO,
                        entryCondition,
                        StrategyCondition.never(),
                        unbalancedLegHandling
                ))
        );
    }

    public PairArbitrageConfig {
        Objects.requireNonNull(strategyId, "strategyId");
        pairs = List.copyOf(pairs);
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("pairs must not be empty");
        }
        HashSet<String> pairIds = new HashSet<>();
        if (pairs.stream().map(ArbitragePair::pairId).anyMatch(pairId -> !pairIds.add(pairId))) {
            throw new IllegalArgumentException("pairId must be unique");
        }
    }
}
