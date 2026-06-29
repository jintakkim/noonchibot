package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;

import java.util.List;
import java.util.Objects;

public class CompositeRiskGate implements RiskGate {
    private final List<RiskGate> gates;

    public CompositeRiskGate(List<RiskGate> gates) {
        Objects.requireNonNull(gates, "gates");
        this.gates = List.copyOf(gates);
    }

    @Override
    public ExecutionPlan approve(ExecutionPlan plan, StrategyContext context) {
        ExecutionPlan approved = plan;
        for (RiskGate gate : gates) {
            approved = gate.approve(approved, context);
        }
        return approved;
    }
}
