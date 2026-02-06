package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;

public record OrderBookEntry(
        long updateId,
        BigDecimal amount,
        BigDecimal price
) {
    public OrderBookEntry(OrderBookEntry orderBookEntry) {
        this(orderBookEntry.updateId, orderBookEntry.amount, orderBookEntry.price);
    }
}