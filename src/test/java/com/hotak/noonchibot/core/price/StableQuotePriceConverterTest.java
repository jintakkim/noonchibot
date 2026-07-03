package com.hotak.noonchibot.core.price;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StableQuotePriceConverterTest {
    private static final Instant RATE_TIMESTAMP = Instant.parse("2026-06-30T00:00:00Z");
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.000000000000000000000001");
    private final StableQuotePriceConverter converter = new StableQuotePriceConverter();

    @Test
    @DisplayName("직접 방향의 스테이블 페어 환율은 가격에 곱해서 quote를 보정한다")
    void convert_multipliesDirectStablePairRate() {
        StablePairPrice stablePair = StablePairPrice.fromTradingPair(
                "USDC-USDT",
                new BigDecimal("0.9995"),
                RATE_TIMESTAMP
        );

        NormalizedQuotePrice result = converter.convert(
                "BTC-USDC",
                new BigDecimal("60000"),
                "USDT",
                stablePair
        );

        assertThat(result.normalizedTradingPair()).isEqualTo("BTC-USDT");
        assertThat(result.normalizedPrice()).isEqualByComparingTo("59970");
        assertThat(result.appliedQuoteRate()).isEqualByComparingTo("0.9995");
        assertThat(result.rateTradingPair()).isEqualTo("USDC-USDT");
        assertThat(result.rateTimestamp()).isEqualTo(RATE_TIMESTAMP);
    }

    @Test
    @DisplayName("반대 방향의 스테이블 페어 환율은 역수로 변환해서 quote를 보정한다")
    void convert_dividesWhenStablePairDirectionIsReversed() {
        StablePairPrice stablePair = StablePairPrice.fromTradingPair(
                "USDC-USDT",
                new BigDecimal("0.9995"),
                RATE_TIMESTAMP
        );

        NormalizedQuotePrice result = converter.convert(
                "BTC-USDT",
                new BigDecimal("60000"),
                "USDC",
                stablePair
        );

        assertThat(result.normalizedTradingPair()).isEqualTo("BTC-USDC");
        BigDecimal expected = new BigDecimal("60000").divide(
                new BigDecimal("0.9995"),
                java.math.MathContext.DECIMAL128
        );
        assertThat(result.normalizedPrice().subtract(expected).abs())
                .isLessThan(PRICE_TOLERANCE);
    }

    @Test
    @DisplayName("이미 목표 quote이면 스테이블 페어 없이 원본 가격을 유지한다")
    void convert_whenQuoteAlreadyMatches_doesNotRequireStablePair() {
        NormalizedQuotePrice result = converter.convert(
                "btc-usdt",
                new BigDecimal("60000"),
                "usdt",
                null
        );

        assertThat(result.originalTradingPair()).isEqualTo("BTC-USDT");
        assertThat(result.normalizedPrice()).isEqualByComparingTo("60000");
        assertThat(result.appliedQuoteRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.rateTradingPair()).isNull();
    }

    @Test
    @DisplayName("관련 없는 스테이블 페어로 quote 보정을 시도하면 예외를 던진다")
    void convert_rejectsUnrelatedStablePair() {
        StablePairPrice stablePair = StablePairPrice.fromTradingPair(
                "DAI-USDT",
                BigDecimal.ONE,
                RATE_TIMESTAMP
        );

        assertThatThrownBy(() -> converter.convert(
                "BTC-USDC",
                new BigDecimal("60000"),
                "USDT",
                stablePair
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot convert USDC to USDT");
    }
}
