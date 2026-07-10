package com.hotak.noonchibot.core.pricegap;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangePriceGap(
        Exchange exchange,
        String sourceTradingPair,
        BigDecimal sourcePrice,
        BigDecimal normalizedPrice,
        BigDecimal gapRate,
        BigDecimal gapBps,
        Instant priceTimestamp,
        BigDecimal appliedQuoteRate,
        Instant quoteRateTimestamp
) {
}
