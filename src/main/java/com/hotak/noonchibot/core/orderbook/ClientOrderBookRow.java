package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;

public record ClientOrderBookRow(
        BigDecimal price,
        BigDecimal amount,
        long updateId
) {}
