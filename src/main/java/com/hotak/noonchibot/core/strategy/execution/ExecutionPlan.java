package com.hotak.noonchibot.core.strategy.execution;

import java.util.ArrayList;
import java.util.List;

public record ExecutionPlan(List<ExecutionCommand> commands) {
    public ExecutionPlan {
        commands = List.copyOf(commands);
    }

    public static ExecutionPlan empty() {
        return new ExecutionPlan(List.of());
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public ExecutionPlan merge(ExecutionPlan other) {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;

        List<ExecutionCommand> merged = new ArrayList<>(commands);
        merged.addAll(other.commands);
        return new ExecutionPlan(merged);
    }
}
