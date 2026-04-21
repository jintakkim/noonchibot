package com.hotak.noonchibot.core.derivative;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@Builder
public class Position {
    private final String tradingPair;
    private final PositionSide positionSide;
    private BigDecimal unrealizedPnl;
    private BigDecimal entryPrice;
    private BigDecimal amount;

    public void update(BigDecimal unrealizedPnl, BigDecimal entryPrice, BigDecimal amount) {
        this.unrealizedPnl = unrealizedPnl;
        this.entryPrice = entryPrice;
        this.amount = amount;
    }
}
