package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;

public record OrderBookEntry(
        long updateId,
        BigDecimal price,
        BigDecimal amount
) {
}
