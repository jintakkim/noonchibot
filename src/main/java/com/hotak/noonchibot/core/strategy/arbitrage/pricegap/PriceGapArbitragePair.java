package com.hotak.noonchibot.core.strategy.arbitrage.pricegap;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageEvaluation;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageLeg;

import java.math.BigDecimal;
import java.util.Objects;

public record PriceGapArbitragePair(
        String pairId,
        ArbitrageLeg longLeg,
        ArbitrageLeg shortLeg,
        BigDecimal totalBaseAmount,
        BigDecimal sliceBaseAmount,
        BigDecimal orderValidityPriceGap,
        BigDecimal entryPriceGap,
        BigDecimal exitPriceGap,
        BigDecimal stopLossPriceGap,
        StrategyCondition<ArbitrageEvaluation> additionalEntryCondition
) {
    public PriceGapArbitragePair {
        Objects.requireNonNull(pairId, "pairId");
        Objects.requireNonNull(longLeg, "longLeg");
        Objects.requireNonNull(shortLeg, "shortLeg");
        Objects.requireNonNull(totalBaseAmount, "totalBaseAmount");
        Objects.requireNonNull(sliceBaseAmount, "sliceBaseAmount");
        Objects.requireNonNull(orderValidityPriceGap, "orderValidityPriceGap");
        Objects.requireNonNull(entryPriceGap, "entryPriceGap");
        Objects.requireNonNull(exitPriceGap, "exitPriceGap");
        if (totalBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalBaseAmount must be positive");
        }
        if (sliceBaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("sliceBaseAmount must be positive");
        }
        if (entryPriceGap.compareTo(orderValidityPriceGap) <= 0) {
            throw new IllegalArgumentException("entryPriceGap must exceed orderValidityPriceGap");
        }
        if (orderValidityPriceGap.compareTo(exitPriceGap) <= 0) {
            throw new IllegalArgumentException("orderValidityPriceGap must exceed exitPriceGap");
        }
        if (stopLossPriceGap != null && exitPriceGap.compareTo(stopLossPriceGap) <= 0) {
            throw new IllegalArgumentException("exitPriceGap must exceed stopLossPriceGap");
        }
        additionalEntryCondition = additionalEntryCondition == null
                ? StrategyCondition.always()
                : additionalEntryCondition;
    }
}
