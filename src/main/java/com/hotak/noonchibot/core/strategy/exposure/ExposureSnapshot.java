package com.hotak.noonchibot.core.strategy.exposure;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;

import java.math.BigDecimal;
import java.util.Objects;

public record ExposureSnapshot(
        String strategyId,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        BigDecimal filledBaseAmount,
        BigDecimal openBuyBaseAmount,
        BigDecimal openSellBaseAmount,
        BigDecimal pendingCancelBuyBaseAmount,
        BigDecimal pendingCancelSellBaseAmount
) {
    public ExposureSnapshot {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(positionSide, "positionSide");
        filledBaseAmount = zeroIfNull(filledBaseAmount);
        openBuyBaseAmount = zeroIfNull(openBuyBaseAmount);
        openSellBaseAmount = zeroIfNull(openSellBaseAmount);
        pendingCancelBuyBaseAmount = zeroIfNull(pendingCancelBuyBaseAmount);
        pendingCancelSellBaseAmount = zeroIfNull(pendingCancelSellBaseAmount);
    }

    public BigDecimal projectedBaseAmount() {
        return filledBaseAmount.add(openBuyBaseAmount).subtract(openSellBaseAmount);
    }

    public boolean hasOpenOrders() {
        return openBuyBaseAmount.signum() > 0 || openSellBaseAmount.signum() > 0;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
