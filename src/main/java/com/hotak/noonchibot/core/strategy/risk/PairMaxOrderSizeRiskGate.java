package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionUrgency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PairMaxOrderSizeRiskGate implements RiskGate {
    private final Map<String, BigDecimal> maxBaseAmountByPairId;

    public PairMaxOrderSizeRiskGate(Map<String, BigDecimal> maxBaseAmountByPairId) {
        Objects.requireNonNull(maxBaseAmountByPairId, "maxBaseAmountByPairId");
        maxBaseAmountByPairId.forEach((pairId, amount) -> {
            if (pairId == null || pairId.isBlank()) {
                throw new IllegalArgumentException("pairId must not be blank");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("max base amount must be positive: " + pairId);
            }
        });
        this.maxBaseAmountByPairId = Map.copyOf(maxBaseAmountByPairId);
    }

    @Override
    public ExecutionPlan approve(ExecutionPlan plan, StrategyContext context) {
        Map<ExecutionGroup, BigDecimal> adjustedAmounts = calculateAdjustedAmounts(plan.commands());
        List<ExecutionCommand> commands = plan.commands().stream()
                .map(command -> resize(command, adjustedAmounts))
                .toList();
        return new ExecutionPlan(commands);
    }

    private Map<ExecutionGroup, BigDecimal> calculateAdjustedAmounts(List<ExecutionCommand> commands) {
        Map<ExecutionGroup, BigDecimal> adjustedAmounts = new HashMap<>();
        for (ExecutionCommand command : commands) {
            if (!(command instanceof ExecutionCommand.SubmitOrder submit)) {
                continue;
            }
            if (isEmergency(submit)) {
                continue;
            }
            BigDecimal maximum = maxBaseAmountByPairId.get(submit.executionGroupId());
            if (maximum == null) {
                continue;
            }
            ExecutionGroup group = new ExecutionGroup(submit.strategyId(), submit.executionGroupId());
            adjustedAmounts.merge(group, submit.candidate().getAmount().min(maximum), BigDecimal::min);
        }
        return adjustedAmounts;
    }

    private ExecutionCommand resize(
            ExecutionCommand command,
            Map<ExecutionGroup, BigDecimal> adjustedAmounts
    ) {
        if (!(command instanceof ExecutionCommand.SubmitOrder submit)) {
            return command;
        }
        if (isEmergency(submit)) {
            return command;
        }
        BigDecimal amount = adjustedAmounts.get(new ExecutionGroup(submit.strategyId(), submit.executionGroupId()));
        if (amount == null || amount.compareTo(submit.candidate().getAmount()) == 0) {
            return command;
        }
        return new ExecutionCommand.SubmitOrder(
                submit.strategyId(),
                submit.executionGroupId(),
                submit.exchange(),
                submit.reason(),
                submit.urgency(),
                copyWithAmount(submit.candidate(), amount)
        );
    }

    private boolean isEmergency(ExecutionCommand.SubmitOrder submit) {
        return submit.urgency() == ExecutionUrgency.EMERGENCY;
    }

    private OrderCandidate copyWithAmount(OrderCandidate candidate, BigDecimal amount) {
        OrderCandidate.Builder builder = OrderCandidate.builder()
                .tradingPair(candidate.getTradingPair())
                .orderType(candidate.getOrderType())
                .tradeType(candidate.getTradeType())
                .amount(amount)
                .price(candidate.getPrice())
                .postOnly(candidate.isPostOnly())
                .timeInForce(candidate.getTimeInForce());
        if (candidate.getReduceOnly() != null) {
            builder.reduceOnly(candidate.getReduceOnly());
        }
        return builder.build();
    }

    private record ExecutionGroup(String strategyId, String pairId) {
    }
}
