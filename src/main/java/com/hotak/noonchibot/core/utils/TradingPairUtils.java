package com.hotak.noonchibot.core.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TradingPairUtils {
    public static String getQuote(String tradingPair) {
        return tradingPair.split("-")[1];
    }

    public static String getTradingPair(String baseAsset, String quoteAsset) {
        return baseAsset.toUpperCase() + "-" + quoteAsset.toUpperCase();
    }
}
