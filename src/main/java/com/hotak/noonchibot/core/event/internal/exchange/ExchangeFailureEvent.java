package com.hotak.noonchibot.core.event.internal.exchange;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.internal.CoreEvent;
import com.hotak.noonchibot.core.exchange.ExchangeOperation;

import java.time.Instant;
import java.util.Objects;

public record ExchangeFailureEvent(
        Exchange exchange,
        ExchangeOperation operation,
        String tradingPair,
        String clientOrderId,
        String exchangeOrderId,
        String strategyId,
        String executionGroupId,
        Throwable cause,
        Instant occurredAt
) implements CoreEvent {
    public ExchangeFailureEvent {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
