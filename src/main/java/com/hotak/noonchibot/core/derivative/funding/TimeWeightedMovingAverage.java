package com.hotak.noonchibot.core.derivative.funding;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class TimeWeightedMovingAverage {
    private static final double LN_2 = Math.log(2.0);
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal calculate(
            List<FundingRatePoint> points,
            Instant at,
            Duration halfLife
    ) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        double halfLifeMillis = halfLife.toMillis();

        for (FundingRatePoint point : points) {
            if (point.fundingTime().isAfter(at)) break;
            long ageMillis = Math.max(0, Duration.between(point.fundingTime(), at).toMillis());
            BigDecimal weight = BigDecimal.valueOf(Math.exp(-LN_2 * ageMillis / halfLifeMillis));
            weightedSum = weightedSum.add(point.dailyRate().multiply(weight, MATH_CONTEXT));
            weightSum = weightSum.add(weight);
        }
        if (weightSum.signum() == 0) {
            throw new IllegalArgumentException("points must contain a value at or before the target time");
        }
        return weightedSum.divide(weightSum, 18, java.math.RoundingMode.HALF_UP);
    }
}
