package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;

import java.util.Objects;

public class EventPublishingVenueExecutionPlanExecutor implements VenueExecutionPlanExecutor {
    private final EventPublisher eventPublisher;

    public EventPublishingVenueExecutionPlanExecutor(EventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    @Override
    public void execute(VenueExecutionPlan plan) {
        for (VenueExecutionCommand command : plan.commands()) {
            execute(command);
        }
    }

    private void execute(VenueExecutionCommand command) {
        if (command instanceof VenueExecutionCommand.SubmitOrder submit) {
            eventPublisher.publish(new OrderEvent.CreateRequested(
                    submit.candidate(),
                    null,
                    submit.exchange()
            ));
            return;
        }
        if (command instanceof VenueExecutionCommand.CancelOrder cancel) {
            eventPublisher.publish(new OrderEvent.CancelRequested(
                    cancel.clientOrderId(),
                    cancel.exchange()
            ));
        }
    }
}
