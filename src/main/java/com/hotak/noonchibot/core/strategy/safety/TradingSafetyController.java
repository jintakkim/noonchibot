package com.hotak.noonchibot.core.strategy.safety;

import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TradingSafetyController implements TradingStateView, AutoCloseable {
    private final AtomicReference<TradingStatus> status = new AtomicReference<>(TradingStatus.RUNNING);
    private final Set<Subscription> failureSubscriptions = ConcurrentHashMap.newKeySet();

    @Override
    public TradingStatus status() {
        return status.get();
    }

    public void pause(Throwable cause) {
        if (status.getAndSet(TradingStatus.TRADING_PAUSED) == TradingStatus.RUNNING) {
            log.error("trading paused after order failure", cause);
        }
    }

    public void resumeAfterReconciliation() {
        status.set(TradingStatus.RUNNING);
        log.info("trading resumed manually");
    }

    public void connect(EventSubscriber eventSubscriber) {
        Objects.requireNonNull(eventSubscriber, "eventSubscriber");
        failureSubscriptions.add(eventSubscriber.subscribe(
                OrderEvent.Failed.class,
                event -> pause(event.throwable()),
                ExecutionPolicy.sequential()
        ));
    }

    @Override
    public void close() {
        failureSubscriptions.forEach(Subscription::close);
        failureSubscriptions.clear();
    }
}
