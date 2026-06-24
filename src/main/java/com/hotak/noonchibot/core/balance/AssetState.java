package com.hotak.noonchibot.core.balance;

import java.math.BigDecimal;
import java.time.Instant;

public record AssetState(
        BigDecimal totalBalance,
        BigDecimal availableBalance,
        Instant timestamp
) {
}
