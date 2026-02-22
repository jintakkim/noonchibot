package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.datatype.OrderType;
import com.hotak.noonchibot.core.datatype.PositionAction;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.trade.fee.TradeFee;

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
        TradeFee tradeFee,
        String exchangeTradeId,
        String exchangeOrderId
) {}
