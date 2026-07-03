package com.hotak.noonchibot.core.pricegap;

import java.util.Locale;
import java.util.Objects;

public record PriceGapSubscriptionKey(String tradingPair) {
    public PriceGapSubscriptionKey {
        Objects.requireNonNull(tradingPair, "tradingPair");
        tradingPair = tradingPair.toUpperCase(Locale.ROOT);
        String[] assets = tradingPair.split("-");
        if (assets.length != 2 || assets[0].isBlank() || assets[1].isBlank()) {
            throw new IllegalArgumentException("invalid tradingPair: " + tradingPair);
        }
    }
}
