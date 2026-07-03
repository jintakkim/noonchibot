package com.hotak.noonchibot.core.pricegap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PriceGapSnapshot(
        PriceGapSubscriptionKey key,
        Instant timestamp,
        long sequence,
        BigDecimal referencePrice,
        List<ExchangePriceGap> exchanges,
        PriceGapSpread spread
) {
    public PriceGapSnapshot {
        exchanges = List.copyOf(exchanges);
    }
}
