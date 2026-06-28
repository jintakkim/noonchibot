package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;

public sealed interface ExecutionCommand permits ExecutionCommand.SubmitOrder, ExecutionCommand.CancelOrder {
    record SubmitOrder(
            String strategyId,
            Exchange exchange,
            String reason,
            OrderCandidate candidate
    ) implements ExecutionCommand {
    }

    record CancelOrder(
            String strategyId,
            Exchange exchange,
            String reason,
            String clientOrderId
    ) implements ExecutionCommand {
    }
}
