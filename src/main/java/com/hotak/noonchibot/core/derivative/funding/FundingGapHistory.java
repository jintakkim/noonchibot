package com.hotak.noonchibot.core.derivative.funding;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record FundingGapHistory(
        String asset,
        Instant from,
        Instant to,
        Duration weightedAverageHalfLife,
        FundingGapStatistics statistics,
        List<FundingExchangeHistory> exchanges,
        List<FundingGapPoint> gaps
) {
    public FundingGapHistory {
        exchanges = List.copyOf(exchanges);
        gaps = List.copyOf(gaps);
    }
}
