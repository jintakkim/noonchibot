package com.hotak.noonchibot.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceUpdateEvent(
        String asset,
        BigDecimal free,
        BigDecimal locked,
        Instant timestamp
) implements ExchangeEvent {
}
