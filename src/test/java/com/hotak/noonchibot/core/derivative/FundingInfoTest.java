package com.hotak.noonchibot.core.derivative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class FundingInfoTest {
    private static final String TRADING_PAIR = "BTCUSDT";
    private static final String FUNDING_COIN = "USDT";

    @Test
    @DisplayName("fundingInterval만 주어지면 fundingInterval만 업데이트한다")
    void updateOnlyFundingIntervalWhenOthersAreNull() {
        // given
        FundingInfo info = new FundingInfo(TRADING_PAIR, FUNDING_COIN, Duration.ofHours(8));
        Instant originalNextFundingTime = Instant.now().plus(Duration.ofHours(4));
        info.update(
                Duration.ofHours(8),
                new BigDecimal("50000"),
                new BigDecimal("0.0001"),
                originalNextFundingTime
        );

        // when
        info.update(Duration.ofHours(4), null, null, null);

        // then
        assertThat(info.getFundingInterval()).isEqualTo(Duration.ofHours(4));
        assertThat(info.getMarkPrice()).isEqualByComparingTo("50000");
        assertThat(info.getFundingRate()).isEqualByComparingTo("0.0001");
        assertThat(info.getNextFundingTime()).isEqualTo(originalNextFundingTime);
    }

    @Test
    @DisplayName("fundingInterval이 null이면 fundingInterval을 제외한 나머지 필드만 업데이트한다")
    void updateAllExceptFundingIntervalWhenIntervalIsNull() {
        // given
        FundingInfo info = new FundingInfo(TRADING_PAIR, FUNDING_COIN, Duration.ofHours(8));
        info.update(
                Duration.ofHours(8),
                new BigDecimal("50000"),
                new BigDecimal("0.0001"),
                Instant.now().plus(Duration.ofHours(4))
        );

        Instant newNextFundingTime = Instant.now().plus(Duration.ofHours(3));

        // when
        info.update(
                null,
                new BigDecimal("51000"),
                new BigDecimal("0.0002"),
                newNextFundingTime
        );

        // then
        assertThat(info.getMarkPrice()).isEqualByComparingTo("51000");
        assertThat(info.getFundingRate()).isEqualByComparingTo("0.0002");
        assertThat(info.getNextFundingTime()).isEqualTo(newNextFundingTime);
        assertThat(info.getFundingInterval()).isEqualTo(Duration.ofHours(8));
    }

    @Nested
    @DisplayName("isInitialized는")
    class IsInitializedTest {

        @Test
        @DisplayName("markPrice, fundingRate, nextFundingTime이 모두 채워져 있으면 true를 반환한다")
        void returnTrueWhenAllRequiredFieldsArePresent() {
            // given
            FundingInfo info = new FundingInfo(TRADING_PAIR, FUNDING_COIN, Duration.ofHours(8));
            info.update(
                    null,
                    new BigDecimal("50000"),
                    new BigDecimal("0.0001"),
                    Instant.now().plus(Duration.ofHours(4))
            );

            // when & then
            assertThat(info.isInitialized()).isTrue();
        }

        @Test
        @DisplayName("생성 직후에는 false를 반환한다")
        void returnFalseRightAfterCreation() {
            FundingInfo info = new FundingInfo(TRADING_PAIR, FUNDING_COIN, Duration.ofHours(8));

            assertThat(info.isInitialized()).isFalse();
        }
    }


}
