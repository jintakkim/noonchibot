package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.derivative.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionUpdateEvent(
        String tradingPair,
        PositionSide positionSide,
        BigDecimal amount,
        BigDecimal entryPrice,
        BigDecimal unrealizedPnl,
        Instant timestamp
) implements Event {
}
