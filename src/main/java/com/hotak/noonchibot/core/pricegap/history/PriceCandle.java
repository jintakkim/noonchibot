package com.hotak.noonchibot.core.pricegap.history;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceCandle(
        Exchange exchange,
        String tradingPair,
        Instant timestamp,
        BigDecimal close
) {}
