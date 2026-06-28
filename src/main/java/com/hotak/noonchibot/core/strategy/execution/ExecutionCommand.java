package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.order.OrderCandidate;

public sealed interface ExecutionCommand
        permits ExecutionCommand.SubmitOrder, ExecutionCommand.CancelOrder, ExecutionCommand.MarkTaskDone {

    record SubmitOrder(String strategyId, String reason, OrderCandidate candidate) implements ExecutionCommand {}

    record CancelOrder(String strategyId, String reason, String clientOrderId) implements ExecutionCommand {}

    record MarkTaskDone(String strategyId, String reason, String taskId) implements ExecutionCommand {}
}
