package com.hotak.noonchibot.core.strategy.arbitrage.condition;

import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitrageEvaluation;
import com.hotak.noonchibot.core.strategy.arbitrage.ArbitragePair;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record PriceGapCondition(
        BigDecimal threshold,
        Comparison comparison
) implements StrategyCondition<ArbitrageEvaluation> {
    public PriceGapCondition {
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(comparison, "comparison");
    }

    public static PriceGapCondition atLeast(BigDecimal threshold) {
        return new PriceGapCondition(threshold, Comparison.AT_LEAST);
    }

    public static PriceGapCondition atMost(BigDecimal threshold) {
        return new PriceGapCondition(threshold, Comparison.AT_MOST);
    }

    @Override
    public boolean matches(ArbitrageEvaluation evaluation) {
        ArbitragePair pair = evaluation.pair();
        StrategyMarketView market = evaluation.context().marketView();
        BigDecimal baseAmount = pair.sliceBaseAmount();
        BigDecimal longPrice = market.executablePrice(
                pair.longLeg().exchange(),
                pair.longLeg().tradingPair(),
                TradeType.BUY,
                baseAmount
        );
        BigDecimal shortPrice = market.executablePrice(
                pair.shortLeg().exchange(),
                pair.shortLeg().tradingPair(),
                TradeType.SELL,
                baseAmount
        );

        BigDecimal longFee = pair.longLeg().executionPolicy().feeModel().estimateFee(
                TradeType.BUY,
                pair.longLeg().executionPolicy().orderStyle().orderType(),
                baseAmount,
                longPrice
        );
        BigDecimal shortFee = pair.shortLeg().executionPolicy().feeModel().estimateFee(
                TradeType.SELL,
                pair.shortLeg().executionPolicy().orderStyle().orderType(),
                baseAmount,
                shortPrice
        );
        BigDecimal effectiveLongPrice = longPrice.add(longFee.divide(baseAmount, 12, RoundingMode.HALF_UP));
        BigDecimal effectiveShortPrice = shortPrice.subtract(shortFee.divide(baseAmount, 12, RoundingMode.HALF_UP));
        BigDecimal gapRate = effectiveShortPrice.subtract(effectiveLongPrice)
                .divide(effectiveLongPrice, 12, RoundingMode.HALF_UP);
        return comparison.matches(gapRate, threshold);
    }

    public enum Comparison {
        AT_LEAST {
            @Override
            boolean matches(BigDecimal value, BigDecimal threshold) {
                return value.compareTo(threshold) >= 0;
            }
        },
        AT_MOST {
            @Override
            boolean matches(BigDecimal value, BigDecimal threshold) {
                return value.compareTo(threshold) <= 0;
            }
        };

        abstract boolean matches(BigDecimal value, BigDecimal threshold);
    }
}
