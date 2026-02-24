package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.datatype.OrderType;

import java.time.Instant;

public record OrderFailureEvent (
        Instant timestamp,
        String orderId,
        OrderType orderType,
        String errorMessage,
        String errorType
) {}
