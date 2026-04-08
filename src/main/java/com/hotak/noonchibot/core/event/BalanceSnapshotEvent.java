package com.hotak.noonchibot.core.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BalanceSnapshotEvent(
        Map<String, BigDecimal> totalBalances,
        Map<String, BigDecimal> availableBalances,
        Instant timestamp
) implements ExchangeEvent {
}
