package com.hotak.noonchibot.connector.hyperliquid;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@RequiredArgsConstructor
class TradePoller implements SmartLifecycle {
    private static final Duration TRADE_POLLING_INTERVAL = Duration.ofMinutes(1);

    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final TaskScheduler taskScheduler;
    private final OrderTracker orderTracker;
    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile boolean running = false;
    private Subscription subscription;

    @Override
    public void start() {
        if (running) {
            return;
        }
        subscription = eventSubscriber.subscribe(
                TradeEvent.PollingRequested.class,
                this::poll,
                ExecutionPolicy.sequential()
        );
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(
                () -> eventPublisher.publish(new TradeEvent.PollingRequested(Exchange.HYPERLIQUID_DERIVATIVE)),
                TRADE_POLLING_INTERVAL
        );
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Phases.TRADE_POLLING;
    }

    @VisibleForTesting
    void poll() {
        for (InFlightOrder inFlightOrder : orderTracker.getAllInFlightOrders()) {
            eventPublisher.publish(new TradeEvent.UpdateRequested(
                    inFlightOrder.getClientOrderId(),
                    inFlightOrder.getExchangeOrderId(),
                    inFlightOrder.getTradingPair()
            ));
        }
    }

    private void poll(TradeEvent.PollingRequested event) {
        if (event.exchange() == Exchange.HYPERLIQUID_DERIVATIVE) poll();
    }
}
