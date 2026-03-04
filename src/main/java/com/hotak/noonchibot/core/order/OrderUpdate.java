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

    public OrderUpdate(
            String tradingPair,
            Instant updateTimestamp,
            InFlightOrder.State newState,
            String clientOrderId,
            String exchangeOrderId
    ) {
       this(tradingPair, updateTimestamp, newState, clientOrderId, exchangeOrderId, null);
    }

    public record OrderFailure(String errorType, String errorMessage) {}
}