package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.model.VenueTargetPosition;

import java.util.List;

public sealed interface VenueStrategyDecision permits VenueStrategyDecision.Noop, VenueStrategyDecision.Targets {
    record Noop(String reason) implements VenueStrategyDecision {
    }

    record Targets(List<VenueTargetPosition> positions) implements VenueStrategyDecision {
        public Targets {
            positions = List.copyOf(positions);
        }
    }
}
