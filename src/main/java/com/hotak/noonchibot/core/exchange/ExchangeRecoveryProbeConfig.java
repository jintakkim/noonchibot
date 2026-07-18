package com.hotak.noonchibot.core.exchange;

import java.time.Duration;
import java.util.Objects;

public record ExchangeRecoveryProbeConfig(
        Duration cooldown,
        Duration probeInterval,
        int requiredConsecutiveSuccesses
) {
    public static final ExchangeRecoveryProbeConfig DEFAULT = new ExchangeRecoveryProbeConfig(
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            2
    );

    public ExchangeRecoveryProbeConfig {
        requirePositive(cooldown, "cooldown");
        requirePositive(probeInterval, "probeInterval");
        if (requiredConsecutiveSuccesses < 1) {
            throw new IllegalArgumentException("requiredConsecutiveSuccesses는 1 이상이어야 합니다.");
        }
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "은 양수여야 합니다.");
        }
    }
}
