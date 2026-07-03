package com.hotak.noonchibot.core.strategy.arbitrage.fee;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class RateFeeModel implements FeeModel {
    private final FeeRateSchedule feeRateSchedule;

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
