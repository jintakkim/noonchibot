package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;

import java.util.Optional;

public record ExchangeRuntimeState(
        Exchange exchange,
        ExchangeRuntimeStatus status,
        Optional<Throwable> lastFailure
) {
}
