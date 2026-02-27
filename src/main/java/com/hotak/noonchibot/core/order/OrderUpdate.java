package com.hotak.noonchibot.core.order;

import java.time.Instant;
import java.util.Map;

public record OrderUpdate(
        String tradingPair,
        Instant updateTimestamp,
        InFlightOrder.State newState,
        String clientOrderId,
        String exchangeOrderId,
        Map<String, Object> miscUpdates
) {}
