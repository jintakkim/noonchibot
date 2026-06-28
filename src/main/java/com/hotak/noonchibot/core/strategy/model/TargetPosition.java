package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TargetPosition(
        String strategyId,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        BigDecimal targetBaseAmount,
        TargetOrderStyle orderStyle,
        Instant validUntil,
        String reason
) {
    public TargetPosition {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(positionSide, "positionSide");
        Objects.requireNonNull(targetBaseAmount, "targetBaseAmount");
        Objects.requireNonNull(orderStyle, "orderStyle");
    }

    public boolean isExpired(Instant now) {
        return validUntil != null && !validUntil.isAfter(now);
    }
}
