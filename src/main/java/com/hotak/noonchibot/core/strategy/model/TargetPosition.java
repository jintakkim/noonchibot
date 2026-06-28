package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.derivative.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record TargetPosition(
        String strategyId,
        String tradingPair,
        PositionSide positionSide,
        BigDecimal targetBaseAmount,
        TargetOrderStyle orderStyle,
        Instant validUntil,
        String reason
) {
    public TargetPosition {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId is required");
        }
        if (tradingPair == null || tradingPair.isBlank()) {
            throw new IllegalArgumentException("tradingPair is required");
        }
        if (positionSide == null) {
            throw new IllegalArgumentException("positionSide is required");
        }
        if (targetBaseAmount == null) {
            throw new IllegalArgumentException("targetBaseAmount is required");
        }
        if (orderStyle == null) {
            throw new IllegalArgumentException("orderStyle is required");
        }
    }

    public boolean isExpired(Instant now) {
        return validUntil != null && !validUntil.isAfter(now);
    }
}
