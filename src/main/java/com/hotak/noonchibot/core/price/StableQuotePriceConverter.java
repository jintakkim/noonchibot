package com.hotak.noonchibot.core.price;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Objects;

public class StableQuotePriceConverter {
    public NormalizedQuotePrice convert(
            String tradingPair,
            BigDecimal price,
            String targetQuoteAsset,
            StablePairPrice stablePairPrice
    ) {
        String[] assets = parseTradingPair(tradingPair);
        String baseAsset = assets[0];
        String sourceQuoteAsset = assets[1];
        String targetQuote = normalizeAsset(targetQuoteAsset, "targetQuoteAsset");
        String originalTradingPair = baseAsset + "-" + sourceQuoteAsset;
        validatePrice(price);

        if (sourceQuoteAsset.equals(targetQuote)) {
            return new NormalizedQuotePrice(
                    originalTradingPair,
                    price,
                    baseAsset + "-" + targetQuote,
                    price,
                    BigDecimal.ONE,
                    null,
                    null
            );
        }

        Objects.requireNonNull(stablePairPrice, "stablePairPrice");
        BigDecimal quoteRate = resolveQuoteRate(sourceQuoteAsset, targetQuote, stablePairPrice);
        return new NormalizedQuotePrice(
                originalTradingPair,
                price,
                baseAsset + "-" + targetQuote,
                price.multiply(quoteRate),
                quoteRate,
                stablePairPrice.tradingPair(),
                stablePairPrice.timestamp()
        );
    }

    private BigDecimal resolveQuoteRate(
            String sourceQuoteAsset,
            String targetQuoteAsset,
            StablePairPrice stablePairPrice
    ) {
        if (stablePairPrice.baseAsset().equals(sourceQuoteAsset)
                && stablePairPrice.quoteAsset().equals(targetQuoteAsset)) {
            return stablePairPrice.price();
        }
        if (stablePairPrice.baseAsset().equals(targetQuoteAsset)
                && stablePairPrice.quoteAsset().equals(sourceQuoteAsset)) {
            return BigDecimal.ONE.divide(stablePairPrice.price(), MathContext.DECIMAL128);
        }
        throw new IllegalArgumentException(
                "stable pair " + stablePairPrice.tradingPair()
                        + " cannot convert " + sourceQuoteAsset + " to " + targetQuoteAsset
        );
    }

    private String[] parseTradingPair(String tradingPair) {
        Objects.requireNonNull(tradingPair, "tradingPair");
        String[] assets = tradingPair.toUpperCase(Locale.ROOT).split("-");
        if (assets.length != 2 || assets[0].isBlank() || assets[1].isBlank()) {
            throw new IllegalArgumentException("invalid tradingPair: " + tradingPair);
        }
        return assets;
    }

    private String normalizeAsset(String asset, String fieldName) {
        Objects.requireNonNull(asset, fieldName);
        if (asset.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return asset.toUpperCase(Locale.ROOT);
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
