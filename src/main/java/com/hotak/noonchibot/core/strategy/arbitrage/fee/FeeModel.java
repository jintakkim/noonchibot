package com.hotak.noonchibot.core.strategy.arbitrage.fee;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;

public interface FeeModel {
    BigDecimal estimateFee(
            TradeType tradeType,
            OrderType orderType,
            BigDecimal baseAmount,
            BigDecimal price
    );
}
