package com.hotak.noonchibot.core.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BalanceSnapshotEvent(
        Map<String, BigDecimal> freeBalances,
        Map<String, BigDecimal> lockedBalances,
        Instant timestamp
) implements ExchangeEvent {
}
