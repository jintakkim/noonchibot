package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.event.ExchangeEvent;

import java.time.Instant;

public record OrderUpdateEvent(
        String tradingPair,
        Instant updateTimestamp,
        OrderState newState,
        String clientOrderId,
        String exchangeOrderId,
        //nullable
        OrderFailure orderFailure
) implements ExchangeEvent {

    public OrderUpdateEvent(
            String tradingPair,
            Instant updateTimestamp,
            OrderState newState,
            String clientOrderId,
            String exchangeOrderId
    ) {
       this(tradingPair, updateTimestamp, newState, clientOrderId, exchangeOrderId, null);
    }

    public record OrderFailure(String errorType, String errorMessage) {}
}