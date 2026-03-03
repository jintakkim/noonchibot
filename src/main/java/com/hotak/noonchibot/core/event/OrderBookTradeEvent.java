package com.hotak.noonchibot.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderBookTradeEvent(
        String tradingPair,
        BigDecimal price,
        BigDecimal amount,
        long tradeId,
        Instant timestamp
) implements ExchangeEvent {}