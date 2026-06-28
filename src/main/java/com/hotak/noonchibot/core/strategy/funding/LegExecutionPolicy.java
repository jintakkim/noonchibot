package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record LegExecutionPolicy(
        TargetOrderStyle orderStyle,
        BigDecimal maxSliceBaseAmount,
        int maxOpenOrders,
        Duration targetTtl,
        FeeModel feeModel
) {
    public LegExecutionPolicy {
        Objects.requireNonNull(orderStyle, "orderStyle");
        Objects.requireNonNull(maxSliceBaseAmount, "maxSliceBaseAmount");
        if (maxSliceBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("maxSliceBaseAmount must be positive");
        }
        if (maxOpenOrders <= 0) {
            throw new IllegalArgumentException("maxOpenOrders must be positive");
        }
        targetTtl = targetTtl == null ? Duration.ofSeconds(10) : targetTtl;
        feeModel = feeModel == null ? new RateFeeModel(FeeRateSchedule.sameRate(BigDecimal.ZERO)) : feeModel;
    }
}
