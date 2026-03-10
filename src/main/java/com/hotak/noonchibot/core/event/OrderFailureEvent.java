package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderUpdateEvent;

import java.time.Instant;

public record OrderFailureEvent (
        Instant timestamp,
        String orderId,
        OrderType orderType,
        OrderUpdateEvent.OrderFailure orderFailure //nullable
) implements ExchangeEvent {}
