package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.trade.TokenAmount;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderFilledEvent(
        Instant timestamp,
        String orderId,
        String pair,
        TradeType tradeType,
        OrderType orderType,
        BigDecimal price,
        BigDecimal amount,
        TokenAmount tradeFee,
        String exchangeTradeId,
        String exchangeOrderId
) implements Event {}
