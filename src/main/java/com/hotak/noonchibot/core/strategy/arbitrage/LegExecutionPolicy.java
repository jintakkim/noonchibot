package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.arbitrage.fee.FeeModel;
import com.hotak.noonchibot.core.strategy.arbitrage.fee.FeeRateSchedule;
import com.hotak.noonchibot.core.strategy.arbitrage.fee.RateFeeModel;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record LegExecutionPolicy(
        TargetOrderStyle orderStyle,
        Duration targetTtl,
        FeeModel feeModel
) {
    public LegExecutionPolicy {
        Objects.requireNonNull(orderStyle, "orderStyle");
        targetTtl = targetTtl == null ? Duration.ofSeconds(10) : targetTtl;
        feeModel = feeModel == null
                ? new RateFeeModel(FeeRateSchedule.sameRate(BigDecimal.ZERO))
                : feeModel;
    }
}
