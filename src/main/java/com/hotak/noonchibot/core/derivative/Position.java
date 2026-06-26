package com.hotak.noonchibot.core.derivative;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
public class Position {
    private final String tradingPair;
    private final PositionSide positionSide;
    private final Instant openedAt;
    /**
     * funding fee는 미포함
     */
    private BigDecimal unrealizedPnl;
    private BigDecimal entryPrice;
    private BigDecimal amount;
    private BigDecimal accumulatedFundingPayment = BigDecimal.ZERO;

    public Position(
            String tradingPair,
            PositionSide positionSide,
            Instant openedAt,
            BigDecimal unrealizedPnl,
            BigDecimal entryPrice,
            BigDecimal amount
    ) {
        this.tradingPair = tradingPair;
        this.positionSide = positionSide;
        this.openedAt = openedAt;
        this.unrealizedPnl = unrealizedPnl;
        this.entryPrice = entryPrice;
        this.amount = amount;
    }

    public void update(BigDecimal unrealizedPnl, BigDecimal entryPrice, BigDecimal amount) {
        this.unrealizedPnl = unrealizedPnl;
        this.entryPrice = entryPrice;
        this.amount = amount;
    }

    public void applyFundingPayment(BigDecimal amount) {
        accumulatedFundingPayment = accumulatedFundingPayment.add(amount);
    }

    public BigDecimal netUnrealizedPnl() {
        return unrealizedPnl.add(accumulatedFundingPayment);
    }
}
