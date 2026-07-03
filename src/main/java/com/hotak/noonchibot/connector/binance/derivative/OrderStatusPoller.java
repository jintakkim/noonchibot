package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * 주기적 주문 상태 호출
 */
@Slf4j
@RequiredArgsConstructor
class OrderStatusPoller implements LifecycleAware {
    private static final Duration POLL_DELAY = Duration.ofSeconds(30);

    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final OrderTracker orderTracker;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
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
                () -> eventPublisher.publish(new OrderEvent.StatusPollingRequested(Exchange.BINANCE_DERIVATIVE)),
                POLL_DELAY
        );
    }

    private void poll(OrderEvent.StatusPollingRequested event) {
        orderTracker.getAllInFlightOrders().forEach(order -> {
            String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(order.getTradingPair());
            eventPublisher.publish(new OrderEvent.StatusUpdateRequested(exchangeSymbol, order.getClientOrderId()));
        });
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
