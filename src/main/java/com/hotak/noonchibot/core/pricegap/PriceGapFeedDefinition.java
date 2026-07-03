package com.hotak.noonchibot.core.pricegap;

import com.hotak.noonchibot.core.Exchange;

import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public record PriceGapFeedDefinition(
        PriceGapSubscriptionKey key,
        Map<Exchange, String> sourceTradingPairs,
        Exchange stableRateExchange,
        String stableRateTradingPair
) {
    public PriceGapFeedDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sourceTradingPairs, "sourceTradingPairs");
        sourceTradingPairs = sourceTradingPairs.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toUpperCase(Locale.ROOT)
                ));
        if (sourceTradingPairs.isEmpty()) {
            throw new IllegalArgumentException("sourceTradingPairs must not be empty");
        }
        if ((stableRateExchange == null) != (stableRateTradingPair == null)) {
            throw new IllegalArgumentException("stable rate exchange and pair must be configured together");
        }
        if (stableRateTradingPair != null) {
            stableRateTradingPair = stableRateTradingPair.toUpperCase(Locale.ROOT);
        }
    }
}
