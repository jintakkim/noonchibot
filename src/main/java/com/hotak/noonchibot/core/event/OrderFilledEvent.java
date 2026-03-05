package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.datatype.PositionAction;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderFilledEvent(
        Instant timestamp,
        String orderId,
        String pair,
        TradeType tradeType,
        OrderType orderType,
        BigDecimal price,
        BigDecimal amount,
        List<TokenAmount> tradeFee,
        String exchangeTradeId,
        String exchangeOrderId
) implements ExchangeEvent {}
