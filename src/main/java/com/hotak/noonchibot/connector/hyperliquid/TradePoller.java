package com.hotak.noonchibot.connector.hyperliquid;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@RequiredArgsConstructor
class TradePoller implements LifecycleAware {
    private static final Duration TRADE_POLLING_INTERVAL = Duration.ofMinutes(1);

    private final EventPublisher eventPublisher;
    private final TaskScheduler taskScheduler;
    private final OrderTracker orderTracker;
    private volatile ScheduledFuture<?> scheduledFuture;

    @Override
    public void onStart() {
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(this::poll, TRADE_POLLING_INTERVAL);
    }

    @Override
    public void onShutdown() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    @Override
    public int phase() {
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
}
