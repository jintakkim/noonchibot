package com.hotak.noonchibot.core.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record LastTradePrice(BigDecimal price, Instant timestamp) {
    public LastTradePrice {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(timestamp, "timestamp");
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
