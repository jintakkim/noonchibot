package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;

public sealed interface ExecutionCommand permits ExecutionCommand.SubmitOrder, ExecutionCommand.CancelOrder, ExecutionCommand.ModifyOrder {
    record SubmitOrder(
            String strategyId,
            String executionGroupId,
            Exchange exchange,
            String reason,
            ExecutionUrgency urgency,
            OrderCandidate candidate
    ) implements ExecutionCommand {
        public SubmitOrder(
                String strategyId,
                String executionGroupId,
                Exchange exchange,
                String reason,
                OrderCandidate candidate
        ) {
            this(strategyId, executionGroupId, exchange, reason, ExecutionUrgency.NORMAL, candidate);
        }

        public SubmitOrder(
                String strategyId,
                Exchange exchange,
                String reason,
                OrderCandidate candidate
        ) {
            this(
                    strategyId,
                    strategyId + ":" + candidate.getTradingPair(),
                    exchange,
                    reason,
                    ExecutionUrgency.NORMAL,
                    candidate
            );
        }
    }

    record CancelOrder(
            String strategyId,
            Exchange exchange,
            String reason,
            String clientOrderId
    ) implements ExecutionCommand {
    }

    record ModifyOrder(
            String strategyId,
            Exchange exchange,
            String reason,
            String clientOrderId,
            OrderCandidate replacement
    ) implements ExecutionCommand {
    }
}
