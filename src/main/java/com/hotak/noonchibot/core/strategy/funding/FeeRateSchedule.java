package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.util.Objects;

public record FeeRateSchedule(
        BigDecimal marketBuyRate,
        BigDecimal marketSellRate,
        BigDecimal limitBuyRate,
        BigDecimal limitSellRate
) {
    public FeeRateSchedule {
        marketBuyRate = zeroIfNull(marketBuyRate);
        marketSellRate = zeroIfNull(marketSellRate);
        limitBuyRate = zeroIfNull(limitBuyRate);
        limitSellRate = zeroIfNull(limitSellRate);
    }

    public static FeeRateSchedule sameRate(BigDecimal rate) {
        return new FeeRateSchedule(rate, rate, rate, rate);
    }

    public static FeeRateSchedule makerTaker(BigDecimal makerRate, BigDecimal takerRate) {
        return new FeeRateSchedule(takerRate, takerRate, makerRate, makerRate);
    }

    public BigDecimal rateFor(OrderType orderType, TradeType tradeType) {
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(tradeType, "tradeType");
        if (orderType == OrderType.MARKET) {
            return tradeType == TradeType.BUY ? marketBuyRate : marketSellRate;
        }
        if (orderType == OrderType.LIMIT) {
            return tradeType == TradeType.BUY ? limitBuyRate : limitSellRate;
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
