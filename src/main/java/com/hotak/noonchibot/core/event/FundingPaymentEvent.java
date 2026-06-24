package com.hotak.noonchibot.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingPaymentEvent(
        String tradingPair,
        Instant settledAt,
        BigDecimal amount,           // 음수=지급, 양수=수령
        BigDecimal fundingRate,      // 적용된 비율
        BigDecimal positionSize      // 정산 시점 signed size
) implements Event {}