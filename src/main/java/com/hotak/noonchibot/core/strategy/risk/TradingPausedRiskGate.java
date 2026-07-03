package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.safety.TradingStateView;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TradingPausedRiskGate implements RiskGate {
    private final TradingStateView tradingStateView;

    @Override
    public ExecutionPlan approve(ExecutionPlan plan, StrategyContext context) {
        if (!tradingStateView.isPaused()) {
            return plan;
        }
        return new ExecutionPlan(plan.commands().stream()
                .filter(this::allowedWhilePaused)
                .toList());
    }

    private boolean allowedWhilePaused(ExecutionCommand command) {
        if (command instanceof ExecutionCommand.CancelOrder) {
            return true;
        }
        if (command instanceof ExecutionCommand.SubmitOrder submit) {
            return Boolean.TRUE.equals(submit.candidate().getReduceOnly());
        }
        if (command instanceof ExecutionCommand.ModifyOrder modify) {
            return Boolean.TRUE.equals(modify.replacement().getReduceOnly());
        }
        return false;
    }
}
