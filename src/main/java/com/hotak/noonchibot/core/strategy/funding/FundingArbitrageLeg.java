package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;

import java.util.Objects;

public record FundingArbitrageLeg(
        String legId,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        LegExecutionPolicy executionPolicy,
        EntryCondition legCondition
) {
    public FundingArbitrageLeg(
            String legId,
            Exchange exchange,
            String tradingPair,
            PositionSide positionSide,
            LegExecutionPolicy executionPolicy
    ) {
        this(legId, exchange, tradingPair, positionSide, executionPolicy, new AlwaysEnterCondition());
    }

    public FundingArbitrageLeg {
        Objects.requireNonNull(legId, "legId");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(positionSide, "positionSide");
        Objects.requireNonNull(executionPolicy, "executionPolicy");
        legCondition = legCondition == null ? new AlwaysEnterCondition() : legCondition;
    }
}
