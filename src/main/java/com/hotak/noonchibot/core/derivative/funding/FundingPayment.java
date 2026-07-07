package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingPayment(
        String id,
        Exchange exchange,
        String tradingPair,
        PositionSide positionSide,
        BigDecimal amount,
        String asset,
        Instant timestamp
) {
}
