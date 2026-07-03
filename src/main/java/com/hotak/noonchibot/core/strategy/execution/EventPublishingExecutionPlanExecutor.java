package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EventPublishingExecutionPlanExecutor implements ExecutionPlanExecutor {
    private final EventPublisher eventPublisher;

    @Override
    public void execute(ExecutionPlan plan) {
        for (ExecutionCommand command : plan.commands()) {
            execute(command);
        }
    }

    private void execute(ExecutionCommand command) {
        if (command instanceof ExecutionCommand.SubmitOrder submit) {
            eventPublisher.publish(new OrderEvent.CreateRequested(
                    submit.candidate(),
                    null,
                    submit.exchange()
            ));
            return;
        }
        if (command instanceof ExecutionCommand.CancelOrder cancel) {
            eventPublisher.publish(new OrderEvent.CancelRequested(
                    cancel.clientOrderId(),
                    cancel.exchange()
            ));
            return;
        }
        if (command instanceof ExecutionCommand.ModifyOrder modify) {
            eventPublisher.publish(new OrderEvent.ModifyRequested(
                    modify.clientOrderId(),
                    modify.replacement(),
                    modify.exchange()
            ));
        }
    }
}
