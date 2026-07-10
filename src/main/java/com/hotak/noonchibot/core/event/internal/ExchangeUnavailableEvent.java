package com.hotak.noonchibot.core.event.internal;

import com.hotak.noonchibot.core.Exchange;

public record ExchangeUnavailableEvent(
        Exchange exchange,
        String operation,
        String tradingPair,
        Throwable cause
) {}
