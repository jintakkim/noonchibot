package com.hotak.noonchibot.core.pricegap;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;

public record PriceGapSpread(
        Exchange lowestExchange,
        Exchange highestExchange,
        BigDecimal gapRate,
        BigDecimal gapBps
) {
}
