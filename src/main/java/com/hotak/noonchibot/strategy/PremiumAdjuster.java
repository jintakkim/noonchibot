package com.hotak.noonchibot.strategy;

import java.math.BigDecimal;

public interface PremiumAdjuster {
    /**
     * 시장 평균 프리미엄를 제거한 가격 리턴.
     */
    BigDecimal adjust(String baseAsset, BigDecimal normalizedValue);

    /**
     * 현재 인식하고 있는 시장 평균 프리미엄.
     * 0.05면 평균 5% 프리미엄.
     */
    BigDecimal currentMarketPremium();
}