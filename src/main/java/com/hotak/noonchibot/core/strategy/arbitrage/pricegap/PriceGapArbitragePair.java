package com.hotak.noonchibot.core.strategy.arbitrage.pricegap;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageEvaluation;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageLeg;
import com.hotak.noonchibot.core.strategy.arbitrage.UnbalancedLegHandling;

import java.math.BigDecimal;
import java.util.Objects;

public record PriceGapArbitragePair(
        String pairId,
        ArbitrageLeg longLeg,
        ArbitrageLeg shortLeg,
        BigDecimal totalBaseAmount,
        BigDecimal sliceBaseAmount,
        BigDecimal acceptablePositionGap,
        BigDecimal entryPriceGap,
        BigDecimal exitPriceGap,
        BigDecimal stopLossPriceGap,
        StrategyCondition<ArbitrageEvaluation> additionalEntryCondition,
        UnbalancedLegHandling unbalancedLegHandling
) {
    public PriceGapArbitragePair {
        Objects.requireNonNull(pairId, "pairId");
        Objects.requireNonNull(longLeg, "longLeg");
        Objects.requireNonNull(shortLeg, "shortLeg");
        Objects.requireNonNull(totalBaseAmount, "totalBaseAmount");
        Objects.requireNonNull(sliceBaseAmount, "sliceBaseAmount");
        Objects.requireNonNull(acceptablePositionGap, "acceptablePositionGap");
        Objects.requireNonNull(entryPriceGap, "entryPriceGap");
        Objects.requireNonNull(exitPriceGap, "exitPriceGap");
        Objects.requireNonNull(stopLossPriceGap, "stopLossPriceGap");
        if (totalBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalBaseAmount must be positive");
        }
        if (sliceBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("sliceBaseAmount must be positive");
        }
        if (acceptablePositionGap.signum() < 0) {
            throw new IllegalArgumentException("acceptablePositionGap must not be negative");
        }
        if (entryPriceGap.compareTo(exitPriceGap) <= 0) {
            throw new IllegalArgumentException("entryPriceGap must exceed exitPriceGap");
        }
        if (exitPriceGap.compareTo(stopLossPriceGap) <= 0) {
            throw new IllegalArgumentException("exitPriceGap must exceed stopLossPriceGap");
        }
        additionalEntryCondition = additionalEntryCondition == null
                ? StrategyCondition.always()
                : additionalEntryCondition;
        unbalancedLegHandling = unbalancedLegHandling == null
                ? UnbalancedLegHandling.WAIT_FOR_OTHER_LEG
                : unbalancedLegHandling;
    }
}
