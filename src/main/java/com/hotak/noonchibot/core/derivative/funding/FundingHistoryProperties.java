package com.hotak.noonchibot.core.derivative.funding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("market-analytics.funding")
public record FundingHistoryProperties(
        Duration historyWindow,
        Duration weightedAverageHalfLife,
        List<Duration> settlementFetchDelays
) {
    public FundingHistoryProperties {
        historyWindow = positive(historyWindow, "historyWindow");
        weightedAverageHalfLife = positive(weightedAverageHalfLife, "weightedAverageHalfLife");
        settlementFetchDelays = List.copyOf(settlementFetchDelays);
        if (settlementFetchDelays.isEmpty()) {
            throw new IllegalArgumentException("settlementFetchDelays must not be empty");
        }
        settlementFetchDelays.forEach(delay -> positive(delay, "settlementFetchDelay"));
        for (int i = 1; i < settlementFetchDelays.size(); i++) {
            if (settlementFetchDelays.get(i).compareTo(settlementFetchDelays.get(i - 1)) <= 0) {
                throw new IllegalArgumentException("settlementFetchDelays must be strictly increasing");
            }
        }
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
