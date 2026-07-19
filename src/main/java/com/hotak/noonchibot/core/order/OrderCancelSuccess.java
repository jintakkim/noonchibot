package com.hotak.noonchibot.core.order;

import java.time.Instant;

public record OrderCancelSuccess(
        boolean cancelFinalized,
        Instant timestamp
) {
}
