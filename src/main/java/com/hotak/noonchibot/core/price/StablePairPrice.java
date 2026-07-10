package com.hotak.noonchibot.core.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record StablePairPrice(
        String baseAsset,
        String quoteAsset,
        BigDecimal price,
        Instant timestamp
) {
    public StablePairPrice {
        baseAsset = normalizeAsset(baseAsset, "baseAsset");
        quoteAsset = normalizeAsset(quoteAsset, "quoteAsset");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(timestamp, "timestamp");
        if (baseAsset.equals(quoteAsset)) {
            throw new IllegalArgumentException("stable pair assets must differ");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("stable pair price must be positive");
        }
    }

    public static StablePairPrice fromTradingPair(
            String tradingPair,
            BigDecimal price,
            Instant timestamp
    ) {
        String[] assets = parseTradingPair(tradingPair);
        return new StablePairPrice(assets[0], assets[1], price, timestamp);
    }

    public String tradingPair() {
        return baseAsset + "-" + quoteAsset;
    }

    private static String[] parseTradingPair(String tradingPair) {
        Objects.requireNonNull(tradingPair, "tradingPair");
        String[] assets = tradingPair.split("-");
        if (assets.length != 2 || assets[0].isBlank() || assets[1].isBlank()) {
            throw new IllegalArgumentException("invalid tradingPair: " + tradingPair);
        }
        return assets;
    }

    private static String normalizeAsset(String asset, String fieldName) {
        Objects.requireNonNull(asset, fieldName);
        if (asset.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return asset.toUpperCase(Locale.ROOT);
    }
}
