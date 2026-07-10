package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.util.List;

public record FundingExchangeHistory(
        Exchange exchange,
        String tradingPair,
        List<WeightedFundingRatePoint> points
) {
    public FundingExchangeHistory {
        points = List.copyOf(points);
    }
}
