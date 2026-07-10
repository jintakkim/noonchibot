package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;

public record FundingGapSummary(
        String asset,
        Exchange lowestExchange,
        Exchange highestExchange,
        BigDecimal averageGap,
        Exchange weightedLowestExchange,
        Exchange weightedHighestExchange,
        BigDecimal weightedAverageGap
) {}
