package com.hotak.noonchibot.strategy.arbitrage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FindResult(
        BigDecimal orderAmount,
        List<ArbitrageOpportunity> opportunities,
        Instant timestamp
) {
}
