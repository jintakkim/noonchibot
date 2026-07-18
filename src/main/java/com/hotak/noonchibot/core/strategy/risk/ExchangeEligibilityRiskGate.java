package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.exchange.ExchangeEligibilityView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

@RequiredArgsConstructor
public class ExchangeEligibilityRiskGate implements RiskGate {
    private final ExchangeEligibilityView exchangeEligibilityView;

    @Override
    public ExecutionPlan approve(ExecutionPlan plan, StrategyContext context) {
        BlockedSubmits blockedSubmits = findBlockedSubmits(plan);

        return new ExecutionPlan(plan.commands().stream()
                .filter(command -> isAllowed(command, blockedSubmits))
                .toList());
    }

    private BlockedSubmits findBlockedSubmits(ExecutionPlan plan) {
        Set<String> executionGroups = new HashSet<>();
        Set<ExecutionCommand.SubmitOrder> ungroupedOrders =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (ExecutionCommand command : plan.commands()) {
            if (!(command instanceof ExecutionCommand.SubmitOrder submit)
                    || !isNewOrder(submit)
                    || exchangeEligibilityView.isEligible(submit.exchange())) {
                continue;
            }
            if (submit.executionGroupId() == null) {
                ungroupedOrders.add(submit);
            } else {
                executionGroups.add(submit.executionGroupId());
            }
        }
        return new BlockedSubmits(executionGroups, ungroupedOrders);
    }

    private boolean isAllowed(ExecutionCommand command, BlockedSubmits blockedSubmits) {
        if (command instanceof ExecutionCommand.SubmitOrder submit) {
            return !isNewOrder(submit)
                    || !blockedSubmits.contains(submit);
        }
        if (command instanceof ExecutionCommand.ModifyOrder modify) {
            return isReduceOnly(modify.replacement().getReduceOnly())
                    || exchangeEligibilityView.isEligible(modify.exchange());
        }
        return true;
    }

    private boolean isNewOrder(ExecutionCommand.SubmitOrder submit) {
        return !isReduceOnly(submit.candidate().getReduceOnly());
    }

    private boolean isReduceOnly(Boolean reduceOnly) {
        return Boolean.TRUE.equals(reduceOnly);
    }

    private record BlockedSubmits(
            Set<String> executionGroups,
            Set<ExecutionCommand.SubmitOrder> ungroupedOrders
    ) {
        private boolean contains(ExecutionCommand.SubmitOrder submit) {
            return submit.executionGroupId() == null
                    ? ungroupedOrders.contains(submit)
                    : executionGroups.contains(submit.executionGroupId());
        }
    }
}
