package com.hotak.noonchibot.core.order;

import java.time.Instant;

public record OrderCancelResult(
        boolean cancelFinalized,
        Instant timestamp
) {
}
