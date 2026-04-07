package com.hotak.noonchibot.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceUpdateEvent(
        String asset,
        BigDecimal totalBalance,
        BigDecimal availableBalance,
        Instant timestamp
) implements ExchangeEvent {
}
