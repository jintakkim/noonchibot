package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.util.Objects;

public class RateFeeModel implements FeeModel {
    private final FeeRateSchedule feeRateSchedule;

    public RateFeeModel(FeeRateSchedule feeRateSchedule) {
        this.feeRateSchedule = Objects.requireNonNull(feeRateSchedule, "feeRateSchedule");
    }

    @Override
    public BigDecimal estimateFee(
            TradeType tradeType,
            OrderType orderType,
            BigDecimal baseAmount,
            BigDecimal price
    ) {
        if (baseAmount == null || price == null) {
            return BigDecimal.ZERO;
        }
        return baseAmount.abs()
                .multiply(price)
                .multiply(feeRateSchedule.rateFor(orderType, tradeType));
    }
}
