package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@RequiredArgsConstructor
class OrderStatusPoller implements LifecycleAware {
    private static final Duration POLL_DELAY = Duration.ofSeconds(30);

    private final EventPublisher eventPublisher;
    private final OrderTracker orderTracker;
    private final TaskScheduler taskScheduler;
    private volatile ScheduledFuture<?> pollFuture;

    @Override
    public void onStart() {
        pollFuture = taskScheduler.scheduleWithFixedDelay(() ->
                orderTracker.getAllInFlightOrders().forEach(order ->
                        eventPublisher.publish(new OrderEvent.StatusUpdateRequested(
                                order.getTradingPair(),
                                order.getClientOrderId()
                        ))
                ), POLL_DELAY);
    }

    @Override
    public void onShutdown() {
        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }

    @Override
    public int phase() {
        return Phases.ORDER_STATUS_POLLING;
    }
}
