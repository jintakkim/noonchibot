package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;

@FunctionalInterface
public interface ExchangeFailurePolicy {
    FailureDecision decide(ExchangeFailureEvent event);
}
