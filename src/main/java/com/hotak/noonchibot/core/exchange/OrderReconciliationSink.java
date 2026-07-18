package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;

@FunctionalInterface
public interface OrderReconciliationSink {
    OrderReconciliationSink NOOP = event -> {};

    void enqueue(ExchangeFailureEvent event);
}
