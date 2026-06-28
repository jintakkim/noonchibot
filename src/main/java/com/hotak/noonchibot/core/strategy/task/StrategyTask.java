package com.hotak.noonchibot.core.strategy.task;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record StrategyTask(
        String taskId,
        String strategyId,
        StrategyTaskState state,
        Set<String> clientOrderIds,
        BigDecimal targetBaseAmount,
        BigDecimal filledBaseAmount,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        String reason
) {
    public StrategyTask {
        clientOrderIds = clientOrderIds == null ? Set.of() : Set.copyOf(clientOrderIds);
        targetBaseAmount = targetBaseAmount == null ? BigDecimal.ZERO : targetBaseAmount;
        filledBaseAmount = filledBaseAmount == null ? BigDecimal.ZERO : filledBaseAmount;
    }

    public boolean isActive() {
        return state != StrategyTaskState.COMPLETED
                && state != StrategyTaskState.FAILED
                && state != StrategyTaskState.EXPIRED;
    }
}
