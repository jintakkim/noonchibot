package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

import java.math.BigDecimal;
import java.time.Instant;

public sealed interface PositionEvent extends CoreEvent {
    record SnapshotReceived(
            String tradingPair
    ) implements PositionEvent {}

    record UpdateReceived(
            String tradingPair,
            PositionSide positionSide,
            BigDecimal amount,
            BigDecimal entryPrice,
            BigDecimal unrealizedPnl,
            Instant timestamp
    ) implements PositionEvent {}
}
