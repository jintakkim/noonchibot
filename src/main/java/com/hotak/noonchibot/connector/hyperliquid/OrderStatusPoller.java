package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
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
    private final EventSubscriber eventSubscriber;
    private final OrderTracker orderTracker;
    private final TaskScheduler taskScheduler;
    private volatile ScheduledFuture<?> pollFuture;
    private Subscription subscription;

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                OrderEvent.StatusPollingRequested.class,
                this::poll,
                ExecutionPolicy.sequential()
        );
        pollFuture = taskScheduler.scheduleWithFixedDelay(
                () -> eventPublisher.publish(new OrderEvent.StatusPollingRequested(Exchange.HYPERLIQUID_DERIVATIVE)),
                POLL_DELAY
        );
    }

    private void poll(OrderEvent.StatusPollingRequested event) {
        if (event.exchange() != Exchange.HYPERLIQUID_DERIVATIVE) return;
        orderTracker.getAllInFlightOrders().forEach(order ->
                eventPublisher.publish(new OrderEvent.StatusUpdateRequested(
                        order.getTradingPair(),
                        order.getClientOrderId()
                ))
        );
    }

    @Override
    public void onShutdown() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
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
