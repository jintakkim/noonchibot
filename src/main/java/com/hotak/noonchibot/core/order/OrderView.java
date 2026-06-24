package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record OrderView(
        String clientOrderId,
        String exchangeOrderId,
        String tradingPair,

        OrderType orderType,
        TradeType tradeType,
        TimeInForce timeInForce,
        boolean postOnly,

        OrderState state,

        BigDecimal amount,
        BigDecimal price,
        BigDecimal executedBaseAmount,
        BigDecimal executedQuoteAmount,
        BigDecimal remainingBaseAmount,

        Set<String> processedTradeIds,
        Map<String, BigDecimal> accumulatedFees,

        Instant createdAt,
        Instant updatedAt
) {
}