package com.hotak.noonchibot.core.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record NormalizedQuotePrice(
        String originalTradingPair,
        BigDecimal originalPrice,
        String normalizedTradingPair,
        BigDecimal normalizedPrice,
        BigDecimal appliedQuoteRate,
        String rateTradingPair,
        Instant rateTimestamp
) {
    public NormalizedQuotePrice {
        Objects.requireNonNull(originalTradingPair, "originalTradingPair");
        Objects.requireNonNull(originalPrice, "originalPrice");
        Objects.requireNonNull(normalizedTradingPair, "normalizedTradingPair");
        Objects.requireNonNull(normalizedPrice, "normalizedPrice");
        Objects.requireNonNull(appliedQuoteRate, "appliedQuoteRate");
    }
}
