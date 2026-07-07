package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.util.Map;

public record FundingGapMarket(
        String asset,
        Map<Exchange, String> tradingPairsByExchange
) {
    public FundingGapMarket {
        tradingPairsByExchange = Map.copyOf(tradingPairsByExchange);
        if (tradingPairsByExchange.size() < 2) {
            throw new IllegalArgumentException("At least two exchanges are required: " + asset);
        }
    }
}
