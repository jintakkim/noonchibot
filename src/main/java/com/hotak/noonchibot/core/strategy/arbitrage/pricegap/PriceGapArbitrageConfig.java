package com.hotak.noonchibot.core.strategy.arbitrage.pricegap;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record PriceGapArbitrageConfig(
        String strategyId,
        List<PriceGapArbitragePair> pairs
) {
    public PriceGapArbitrageConfig {
        Objects.requireNonNull(strategyId, "strategyId");
        pairs = List.copyOf(pairs);
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("pairs must not be empty");
        }
        HashSet<String> pairIds = new HashSet<>();
        if (pairs.stream().map(PriceGapArbitragePair::pairId).anyMatch(pairId -> !pairIds.add(pairId))) {
            throw new IllegalArgumentException("pairId must be unique");
        }
    }
}
