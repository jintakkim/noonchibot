package com.hotak.noonchibot.core.strategy.arbitrage.pricegap;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureReconciler;
import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageEvaluation;
import com.hotak.noonchibot.core.strategy.arbitrage.PairArbitrageConfig;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitragePair;
import com.hotak.noonchibot.core.strategy.arbitrage.PairArbitrageStrategy;
import com.hotak.noonchibot.core.strategy.arbitrage.condition.PriceGapCondition;

import java.util.List;

public class PriceGapArbitrageStrategy extends PairArbitrageStrategy {
    public PriceGapArbitrageStrategy(
            PriceGapArbitrageConfig config,
            ExposureCalculator exposureCalculator
    ) {
        this(config, exposureCalculator, new ExposureReconciler(ReconcilePolicy.conservative()));
    }

    public PriceGapArbitrageStrategy(
            PriceGapArbitrageConfig config,
            ExposureCalculator exposureCalculator,
            ExposureReconciler exposureReconciler
    ) {
        super(toPairConfig(config), exposureCalculator, exposureReconciler);
    }

    private static PairArbitrageConfig toPairConfig(PriceGapArbitrageConfig config) {
        List<ArbitragePair> pairs = config.pairs().stream()
                .map(PriceGapArbitrageStrategy::toArbitragePair)
                .toList();
        return new PairArbitrageConfig(config.strategyId(), pairs);
    }

    private static ArbitragePair toArbitragePair(PriceGapArbitragePair pair) {
        StrategyCondition<ArbitrageEvaluation> entryCondition = PriceGapCondition
                .atLeast(pair.entryPriceGap())
                .and(pair.additionalEntryCondition());
        return new ArbitragePair(
                pair.pairId(),
                pair.longLeg(),
                pair.shortLeg(),
                pair.totalBaseAmount(),
                pair.sliceBaseAmount(),
                PriceGapCondition.atLeast(pair.orderValidityPriceGap()),
                entryCondition,
                PriceGapCondition.atMost(pair.exitPriceGap()),
                pair.stopLossPriceGap() == null
                        ? StrategyCondition.never()
                        : PriceGapCondition.atMost(pair.stopLossPriceGap())
        );
    }
}
