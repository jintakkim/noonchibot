package com.hotak.noonchibot.core.strategy.execution;

import java.util.ArrayList;
import java.util.List;

public record VenueExecutionPlan(List<VenueExecutionCommand> commands) {
    public VenueExecutionPlan {
        commands = List.copyOf(commands);
    }

    public static VenueExecutionPlan empty() {
        return new VenueExecutionPlan(List.of());
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public VenueExecutionPlan merge(VenueExecutionPlan other) {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;

        List<VenueExecutionCommand> merged = new ArrayList<>(commands);
        merged.addAll(other.commands);
        return new VenueExecutionPlan(merged);
    }
}
