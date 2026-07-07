package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record WeightedFundingRatePoint(
        Exchange exchange,
        String tradingPair,
        Instant fundingTime,
        BigDecimal fundingRate,
        BigDecimal dailyRate,
        BigDecimal weightedAverageDailyRate
) {}
