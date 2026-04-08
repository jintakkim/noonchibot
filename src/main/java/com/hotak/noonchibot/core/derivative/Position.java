package com.hotak.noonchibot.core.derivative;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class Position {
    private final String tradingPair;
    private final PositionSide positionSide;
    private final BigDecimal unrealizedPnl;
    private final BigDecimal entryPrice;
    private final BigDecimal amount;
    private final int leverage;
}
