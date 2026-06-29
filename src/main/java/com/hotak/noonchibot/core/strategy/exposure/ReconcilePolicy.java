package com.hotak.noonchibot.core.strategy.exposure;

import java.math.BigDecimal;

public record ReconcilePolicy(BigDecimal minDeltaBaseAmount) {
    public ReconcilePolicy {
        minDeltaBaseAmount = minDeltaBaseAmount == null ? BigDecimal.ZERO : minDeltaBaseAmount;
        if (minDeltaBaseAmount.signum() < 0) {
            throw new IllegalArgumentException("minDeltaBaseAmount must not be negative");
        }
    }

    public static ReconcilePolicy conservative() {
        return new ReconcilePolicy(BigDecimal.ZERO);
    }
}
