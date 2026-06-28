package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import java.util.List;

public sealed interface StrategyDecision permits StrategyDecision.Noop, StrategyDecision.Targets {
    record Noop(String reason) implements StrategyDecision {}

    record Targets(List<TargetPosition> positions) implements StrategyDecision {
        public Targets {
            positions = List.copyOf(positions);
        }
    }
}
