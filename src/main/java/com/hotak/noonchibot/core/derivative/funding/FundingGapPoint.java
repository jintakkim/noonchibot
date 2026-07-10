package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingGapPoint(
        Instant timestamp,
        Exchange lowestExchange,
        Exchange highestExchange,
        BigDecimal lowestDailyRate,
        BigDecimal highestDailyRate,
        BigDecimal currentGap,
        Exchange weightedLowestExchange,
        Exchange weightedHighestExchange,
        BigDecimal weightedAverageGap
) {}
