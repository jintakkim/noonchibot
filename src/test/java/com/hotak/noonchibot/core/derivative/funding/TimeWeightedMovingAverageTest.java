package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.Exchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWeightedMovingAverageTest {
    @Test
    @DisplayName("반감기만큼 오래된 펀딩률에는 최신 펀딩률의 절반 가중치를 적용한다")
    void calculateAppliesTimeDecay() {
        Instant now = Instant.parse("2026-07-03T00:00:00Z");
        List<FundingRatePoint> points = List.of(
                point(now.minus(Duration.ofHours(24)), "0.01"),
                point(now, "0.02")
        );

        BigDecimal average = new TimeWeightedMovingAverage()
                .calculate(points, now, Duration.ofHours(24));

        assertThat(average).isEqualByComparingTo("0.016666666666666667");
    }

    private FundingRatePoint point(Instant time, String dailyRate) {
        return new FundingRatePoint(
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                time,
                new BigDecimal(dailyRate),
                Duration.ofDays(1)
        );
    }
}
