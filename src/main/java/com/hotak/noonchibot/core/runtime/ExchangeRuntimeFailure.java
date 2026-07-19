package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;

import java.time.Instant;

public record ExchangeRuntimeFailure(
        Exchange exchange,
        String component,
        Throwable cause,
        Instant occurredAt
) {
}
