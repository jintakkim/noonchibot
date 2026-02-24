package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.datatype.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record SellOrderCompletedEvent(
        Instant timestamp,
        OrderType type,
        String tradingPair,
        BigDecimal amount,
        BigDecimal price,
        String orderId,
        Instant creationTimestamp,
        String exchangeOrderId
) {}