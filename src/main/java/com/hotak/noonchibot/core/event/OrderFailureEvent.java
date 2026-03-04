package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderUpdate;

import java.time.Instant;

public record OrderFailureEvent (
        Instant timestamp,
        String orderId,
        OrderType orderType,
        OrderUpdate.OrderFailure orderFailure
) {}
