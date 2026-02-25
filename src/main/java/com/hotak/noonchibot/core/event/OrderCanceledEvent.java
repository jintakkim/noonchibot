package com.hotak.noonchibot.core.event;

import java.time.Instant;

public record OrderCanceledEvent(
    Instant timestamp,
    String orderId,
    String exchangeOrderId
) {}
