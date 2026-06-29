package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeRiskGateTest {
    @Test
    void approve_appliesGatesInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        CompositeRiskGate gate = new CompositeRiskGate(List.of(
                (plan, context) -> {
                    calls.add("size");
                    return plan;
                },
                (plan, context) -> {
                    calls.add("balance");
                    return plan;
                }
        ));

        gate.approve(ExecutionPlan.empty(), null);

        assertThat(calls).containsExactly("size", "balance");
    }
}
