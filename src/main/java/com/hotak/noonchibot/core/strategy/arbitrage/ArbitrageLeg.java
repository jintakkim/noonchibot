package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.strategy.api.StrategyCondition;

import java.util.Objects;

public record ArbitrageLeg(
        String legId,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        LegExecutionPolicy executionPolicy,
        StrategyCondition<ArbitrageEvaluation> legCondition
) {
    public ArbitrageLeg(
            String legId,
            Exchange exchange,
            String tradingPair,
            PositionSide positionSide,
            LegExecutionPolicy executionPolicy
    ) {
        this(legId, exchange, tradingPair, positionSide, executionPolicy, StrategyCondition.always());
    }

    public ArbitrageLeg {
        Objects.requireNonNull(legId, "legId");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(positionSide, "positionSide");
        Objects.requireNonNull(executionPolicy, "executionPolicy");
        legCondition = legCondition == null ? StrategyCondition.always() : legCondition;
    }
}
