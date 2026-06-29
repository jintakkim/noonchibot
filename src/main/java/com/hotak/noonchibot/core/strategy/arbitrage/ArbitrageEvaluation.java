package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.exposure.ExposureSnapshot;

import java.util.Objects;

public record ArbitrageEvaluation(
        StrategyContext context,
        ArbitragePair pair,
        ExposureSnapshot longExposure,
        ExposureSnapshot shortExposure
) {
    public ArbitrageEvaluation {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(longExposure, "longExposure");
        Objects.requireNonNull(shortExposure, "shortExposure");
    }
}
