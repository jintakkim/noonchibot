package com.hotak.noonchibot.core.strategy.funding;

import java.math.BigDecimal;
import java.util.Objects;

public record FundingArbitrageConfig(
        String strategyId,
        FundingArbitrageLeg longLeg,
        FundingArbitrageLeg shortLeg,
        BigDecimal totalBaseAmount,
        EntryCondition entryCondition,
        UnbalancedLegHandling unbalancedLegHandling
) {
    public FundingArbitrageConfig {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(longLeg, "longLeg");
        Objects.requireNonNull(shortLeg, "shortLeg");
        Objects.requireNonNull(totalBaseAmount, "totalBaseAmount");
        if (totalBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalBaseAmount must be positive");
        }
        entryCondition = entryCondition == null ? new AlwaysEnterCondition() : entryCondition;
        unbalancedLegHandling = unbalancedLegHandling == null
                ? UnbalancedLegHandling.WAIT_FOR_OTHER_LEG
                : unbalancedLegHandling;
    }
}
