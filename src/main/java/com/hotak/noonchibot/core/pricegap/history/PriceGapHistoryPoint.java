package com.hotak.noonchibot.core.pricegap.history;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceGapHistoryPoint(
        Instant timestamp,
        Exchange lowestExchange,
        Exchange highestExchange,
        BigDecimal gapRate,
        BigDecimal gapBps
) {}
