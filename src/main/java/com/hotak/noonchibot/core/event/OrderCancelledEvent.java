package com.hotak.noonchibot.core.event;

import java.time.Instant;

public record OrderCancelledEvent(
    Instant timestamp,
    String orderId,
    String exchangeOrderId
) {}
