package com.hotak.noonchibot.core.order;

import java.time.Instant;

public record OrderUpdate(
        String tradingPair,
        Instant updateTimestamp,
        InFlightOrder.State newState,
        String clientOrderId,
        String exchangeOrderId,
        //nullable
        OrderFailure orderFailure

) {
    public record OrderFailure(String errorType, String errorMessage) {}
}
