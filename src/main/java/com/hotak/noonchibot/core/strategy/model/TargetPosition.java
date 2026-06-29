package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.strategy.execution.ExecutionUrgency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TargetPosition(
        String strategyId,
        String executionGroupId,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        BigDecimal targetBaseAmount,
        TargetOrderStyle orderStyle,
        Instant validUntil,
        String reason,
        ExecutionUrgency urgency
) {
    public TargetPosition(
            String strategyId,
            Exchange exchange,
            String tradingPair,
            PositionSide positionSide,
            BigDecimal targetBaseAmount,
            TargetOrderStyle orderStyle,
            Instant validUntil,
            String reason
    ) {
        this(
                strategyId,
                strategyId + ":" + tradingPair,
                exchange,
                tradingPair,
                positionSide,
                targetBaseAmount,
                orderStyle,
                validUntil,
                reason,
                ExecutionUrgency.NORMAL
        );
    }

    public TargetPosition {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(executionGroupId, "executionGroupId");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(positionSide, "positionSide");
        Objects.requireNonNull(targetBaseAmount, "targetBaseAmount");
        Objects.requireNonNull(orderStyle, "orderStyle");
        Objects.requireNonNull(urgency, "urgency");
    }

    public boolean isExpired(Instant now) {
        return validUntil != null && !validUntil.isAfter(now);
    }
}
