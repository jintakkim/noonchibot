package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.Exchange;

import java.time.Instant;
import java.util.Objects;

/**
 * 거래소별 신규 주문 허용 상태의 불변 스냅샷이다.
 */
public record ExchangeEligibility(
        Exchange exchange,
        ExchangeEligibilityStatus status,
        int consecutiveFailures,
        Throwable lastFailure,
        Instant updatedAt
) {
    public ExchangeEligibility {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("consecutiveFailures must not be negative");
        }
    }
}
