package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;

public sealed interface VenueExecutionCommand permits VenueExecutionCommand.SubmitOrder, VenueExecutionCommand.CancelOrder {
    record SubmitOrder(
            String strategyId,
            Exchange exchange,
            String reason,
            OrderCandidate candidate
    ) implements VenueExecutionCommand {
    }

    record CancelOrder(
            String strategyId,
            Exchange exchange,
            String reason,
            String clientOrderId
    ) implements VenueExecutionCommand {
    }
}
