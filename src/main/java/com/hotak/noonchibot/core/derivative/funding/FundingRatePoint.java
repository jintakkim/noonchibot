package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record FundingRatePoint(
        Exchange exchange,
        String tradingPair,
        Instant fundingTime,
        BigDecimal fundingRate,
        Duration fundingInterval
) {
    public FundingRatePoint {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(tradingPair, "tradingPair");
        Objects.requireNonNull(fundingTime, "fundingTime");
        Objects.requireNonNull(fundingRate, "fundingRate");
        Objects.requireNonNull(fundingInterval, "fundingInterval");
        if (fundingInterval.isZero() || fundingInterval.isNegative()) {
            throw new IllegalArgumentException("fundingInterval must be positive");
        }
    }

    public BigDecimal dailyRate() {
        return fundingRate.multiply(BigDecimal.valueOf(Duration.ofDays(1).toSeconds()))
                .divide(BigDecimal.valueOf(fundingInterval.toSeconds()), 18, java.math.RoundingMode.HALF_UP);
    }
}
