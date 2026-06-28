package com.hotak.noonchibot.core.strategy.exposure;

import java.math.BigDecimal;
import java.time.Duration;

public record ReconcilePolicy(
        BigDecimal minDeltaBaseAmount,
        boolean cancelStaleOrdersBeforeSubmitting,
        Duration targetGracePeriod
) {
    public ReconcilePolicy {
        minDeltaBaseAmount = minDeltaBaseAmount == null ? BigDecimal.ZERO : minDeltaBaseAmount;
    }

    public static ReconcilePolicy conservative() {
        return new ReconcilePolicy(BigDecimal.ZERO, true, Duration.ZERO);
    }
}
