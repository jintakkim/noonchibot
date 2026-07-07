package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;

public record FundingExchangeStatistics(
        Exchange exchange,
        String tradingPair,
        BigDecimal averageDailyRate,
        BigDecimal weightedAverageDailyRate
) {}
