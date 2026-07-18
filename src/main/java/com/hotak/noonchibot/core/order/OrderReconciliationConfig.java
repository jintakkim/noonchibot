package com.hotak.noonchibot.core.order;

import java.time.Duration;
import java.util.Objects;

public record OrderReconciliationConfig(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration unresolvedRetryDelay
) {
    public static final OrderReconciliationConfig DEFAULT = new OrderReconciliationConfig(
            3,
            Duration.ofSeconds(1),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5)
    );

    public OrderReconciliationConfig {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts는 1 이상이어야 합니다.");
        }
        requirePositive(initialBackoff, "initialBackoff");
        requirePositive(maxBackoff, "maxBackoff");
        requirePositive(unresolvedRetryDelay, "unresolvedRetryDelay");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff은 initialBackoff 이상이어야 합니다.");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "은 양수여야 합니다.");
        }
    }
}
