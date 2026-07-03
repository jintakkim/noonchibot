package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;

import java.util.Objects;

public record ArbitrageLeg(
        String legId,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        LegExecutionPolicy executionPolicy
) {
    public ArbitrageLeg {
        Objects.requireNonNull(legId, "legId");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(positionSide, "positionSide");
        Objects.requireNonNull(executionPolicy, "executionPolicy");
    }
}
