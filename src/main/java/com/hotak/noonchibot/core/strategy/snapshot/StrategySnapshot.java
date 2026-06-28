package com.hotak.noonchibot.core.strategy.snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record StrategySnapshot(
        String strategyId,
        Instant timestamp,
        String type,
        BigDecimal pnl,
        BigDecimal pnlDelta,
        Map<String, Object> metrics
) {
    public StrategySnapshot(
            String strategyId,
            Instant timestamp,
            String type,
            Map<String, Object> metrics
    ) {
        this(strategyId, timestamp, type, BigDecimal.ZERO, BigDecimal.ZERO, metrics);
    }

    public StrategySnapshot(
            String strategyId,
            Instant timestamp,
            String type,
            BigDecimal pnlDelta,
            Map<String, Object> metrics
    ) {
        this(strategyId, timestamp, type, BigDecimal.ZERO, pnlDelta, metrics);
    }

    public StrategySnapshot {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        pnl = pnl == null ? BigDecimal.ZERO : pnl;
        pnlDelta = pnlDelta == null ? BigDecimal.ZERO : pnlDelta;
        Objects.requireNonNull(metrics, "metrics");
        metrics = Map.copyOf(metrics);
    }
}
