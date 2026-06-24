package com.hotak.noonchibot.strategy.arbitrage;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeQuote(
        String exchangeId,
        boolean isSpot,
        String quoteAsset,                           // 거래소의 기본 quoteAsset (ex, upbit라면 KRW)
        BigDecimal effectiveBuyPrice,                // orderAmount 기준 VWAP 매수가
        BigDecimal effectiveSellPrice,               // orderAmount 기준 VWAP 매도가
        String normalizedQuoteAsset ,                // 비교의 기준이 된 기반 자산(ex, USDT)
        BigDecimal effectiveNormalizedBuyPrice,      // orderAmount 기준 정규화된 VWAP 매수가
        BigDecimal effectiveNormalizedSellPrice,     // orderAmount 기준 정규화된 VWAP 매도가
        BigDecimal fundingRate,                      // SPOT이면 null
        Instant nextFundingTime,                     // SPOT이면 null
        BigDecimal annualizedFundingRate             // SPOT이면 null
) {
}
