package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;

import java.math.BigDecimal;

public record TargetOrderStyle(
        OrderType orderType,
        BigDecimal limitPrice,
        TimeInForce timeInForce,
        boolean postOnly,
        boolean reduceOnly
) {
    public TargetOrderStyle {
        if (orderType == null) {
            throw new IllegalArgumentException("orderType is required");
        }
        if (orderType == OrderType.LIMIT && limitPrice == null) {
            throw new IllegalArgumentException("limitPrice is required for LIMIT order");
        }
        if (timeInForce == null) {
            timeInForce = TimeInForce.GTC;
        }
    }

    public static TargetOrderStyle limit(BigDecimal limitPrice, TimeInForce timeInForce, boolean postOnly) {
        return new TargetOrderStyle(OrderType.LIMIT, limitPrice, timeInForce, postOnly, false);
    }

    public static TargetOrderStyle market() {
        return new TargetOrderStyle(OrderType.MARKET, null, TimeInForce.GTC, false, false);
    }

    public static TargetOrderStyle marketReduceOnly() {
        return new TargetOrderStyle(OrderType.MARKET, null, TimeInForce.GTC, false, true);
    }
}
