package com.hotak.noonchibot.strategy;

import java.math.BigDecimal;

public interface PriceNormalizer {
    BigDecimal normalizeValue(String quoteAsset, BigDecimal value);
    String getTargetAsset();
}
