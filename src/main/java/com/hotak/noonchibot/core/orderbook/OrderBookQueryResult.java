package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;

public record OrderBookQueryResult(
        BigDecimal queryPrice,
        BigDecimal queryVolume,
        BigDecimal resultPrice,
        BigDecimal resultVolume
) {
}
