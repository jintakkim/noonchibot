package com.hotak.noonchibot.core.event.internal.exchange;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.internal.CoreEvent;
import com.hotak.noonchibot.core.exchange.ExchangeOperation;

import java.time.Instant;
import java.util.Objects;

public record ExchangeOperationSucceededEvent(
        Exchange exchange,
        ExchangeOperation operation,
        Instant occurredAt
) implements CoreEvent {
    public ExchangeOperationSucceededEvent {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
