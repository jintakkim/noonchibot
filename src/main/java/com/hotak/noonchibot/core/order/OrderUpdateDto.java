package com.hotak.noonchibot.core.order;

import java.time.Instant;

public record OrderUpdateDto(
        String tradingPair,
        Instant updateTimestamp,
        OrderState newState,
        String clientOrderId,
        String exchangeOrderId
) {
}