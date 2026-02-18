package com.hotak.noonchibot.connector;

import java.time.Instant;

public record OrderPlacedDto(
        String exchangeOrderId,
        Instant timestamp
) {
}
