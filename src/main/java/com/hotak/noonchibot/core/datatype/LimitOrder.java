package com.hotak.noonchibot.core.datatype;

import java.math.BigDecimal;
import java.time.Instant;

public record LimitOrder(
        String clientOrderId,
        String tradingPair,
        OrderType orderType,
        String baseAsset,
        String quoteAsset,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal filledAmount,
        Instant creationTimestamp // 마이크로초 단위
) {}