package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.util.List;

public record FundingGapStatistics(
        List<FundingExchangeStatistics> exchanges,
        Exchange lowestExchange,
        Exchange highestExchange,
        BigDecimal averageGap,
        Exchange weightedLowestExchange,
        Exchange weightedHighestExchange,
        BigDecimal weightedAverageGap
) {
    public FundingGapStatistics {
        exchanges = List.copyOf(exchanges);
    }
}
