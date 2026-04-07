package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingInfoMessage(
        String tradingPair,
        Instant eventTime,
        BigDecimal markPrice,
        BigDecimal fundingRate,
        Instant nextFundingTime
) {
}
