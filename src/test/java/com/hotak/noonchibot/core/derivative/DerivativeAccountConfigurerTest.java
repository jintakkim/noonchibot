package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

public class DerivativeAccountConfigurerTest {
    private TestEventPublisher eventPublisher;
    private DerivativeInfoTracker tracker;
    private DerivativeAccountConfigurer configurer;

    @BeforeEach
    void setup() {
        eventPublisher = new TestEventPublisher();
        tracker = Mockito.mock(DerivativeInfoTracker.class);
        configurer = new DerivativeAccountConfigurer(tracker, eventPublisher);
    }

    @Nested
    @DisplayName("PositionMode 테스트")
    class PositionModeContract {
        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 같으면 거래소 요청을 하지 않고 이벤트를 발행한다.")
        void ensurePositionMode_whenCacheMatchesDesired_skipsRequest() {
            when(tracker.findPositionMode()).thenReturn(Optional.of(PositionMode.HEDGE));
            configurer.ensurePositionMode(new PositionModeChangeEvent.EnsureCommand(PositionMode.HEDGE), null);
            var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeEvent.Applied.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> assertThat(event.changedTo()).isEqualTo(PositionMode.HEDGE));

        }

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 다르면 거래소 요청을 한다.")
        void ensurePositionMode_whenCacheDiffers_publishesIORequest() {
            when(tracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
            configurer.ensurePositionMode(new PositionModeChangeEvent.EnsureCommand(PositionMode.HEDGE), null);
            var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeEvent.IORequested.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> assertThat(event.wantTo()).isEqualTo(PositionMode.HEDGE));
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 없으면 거래소 요청을 한다.")
        void ensurePositionMode_whenCacheEmpty_publishesIORequest() {
            when(tracker.findPositionMode()).thenReturn(Optional.empty());
            configurer.ensurePositionMode(new PositionModeChangeEvent.EnsureCommand(PositionMode.HEDGE), null);
            var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeEvent.IORequested.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> assertThat(event.wantTo()).isEqualTo(PositionMode.HEDGE));
        }


        @Test
        @DisplayName("wantTo가 null이면 실패 이벤트가 발생된다.")
        void ensurePositionMode_whenDesiredIsNull_publishesFailed() {
            when(tracker.findPositionMode()).thenReturn(Optional.empty());
            configurer.ensurePositionMode(new PositionModeChangeEvent.EnsureCommand(null), null);
            var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeEvent.Failed.class);
            assertThat(occurred).isPresent();
        }
    }

    @Nested
    @DisplayName("Leverage 테스트")
    class LeverageContract {
        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 같으면 거래소 요청을 하지 않고 이벤트를 발행한다.")
        void ensureLeverage_whenCacheMatchesDesired_skipsRequestAndPublishesApplied() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.of(4));

            configurer.ensureLeverage(new LeverageChangeEvent.EnsureCommand("BTC-USDT", 4), null);

            var occurred = eventPublisher.getFirstEventOfType(LeverageChangeEvent.Applied.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.changedTo()).isEqualTo(4);
                    });
            assertThat(eventPublisher.hasEventOfType(LeverageChangeEvent.IORequested.class)).isFalse();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 다르면 거래소 요청을 한다.")
        void ensureLeverage_whenCacheDiffersFromDesired_publishesIORequest() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.of(10));

            configurer.ensureLeverage(new LeverageChangeEvent.EnsureCommand("BTC-USDT", 4), null);

            var occurred = eventPublisher.getFirstEventOfType(LeverageChangeEvent.IORequested.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.wantTo()).isEqualTo(4);
                    });
            assertThat(eventPublisher.hasEventOfType(LeverageChangeEvent.Applied.class)).isFalse();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 없으면 거래소 요청을 한다.")
        void ensureLeverage_whenCacheEmpty_publishesIORequest() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.empty());

            configurer.ensureLeverage(
                    new LeverageChangeEvent.EnsureCommand("BTC-USDT", 4), null);

            var occurred = eventPublisher.getFirstEventOfType(LeverageChangeEvent.IORequested.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.wantTo()).isEqualTo(4);
                    });
        }

        @Test
        @DisplayName("wantTo가 음수면 실패 이벤트가 발행된다.")
        void ensureLeverage_whenWantToIsNegative_publishesFailed() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.empty());

            configurer.ensureLeverage(new LeverageChangeEvent.EnsureCommand("BTC-USDT", -1), null);

            var occurred = eventPublisher.getFirstEventOfType(LeverageChangeEvent.Failed.class);
            assertThat(occurred).isPresent();
            assertThat(eventPublisher.hasEventOfType(LeverageChangeEvent.IORequested.class)).isFalse();
        }
    }


    @Nested
    @DisplayName("MarginMode 테스트")
    class MarginModeContract {

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 같으면 거래소 요청을 하지 않고 이벤트를 발행한다.")
        void ensureMarginMode_whenCacheMatchesDesired_skipsRequest() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.ISOLATED));

            configurer.ensureMarginMode(new MarginModeChangeEvent.EnsureCommand("BTC-USDT", MarginMode.ISOLATED), null);

            var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeEvent.Applied.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.changedTo()).isEqualTo(MarginMode.ISOLATED);
                    });
            assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.IORequested.class)).isFalse();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 다르면 거래소 요청을 한다.")
        void ensureMarginMode_whenCacheDiffers_publishesIORequest() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.CROSS));

            configurer.ensureMarginMode(new MarginModeChangeEvent.EnsureCommand("BTC-USDT", MarginMode.ISOLATED), null);

            var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeEvent.IORequested.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.wantTo()).isEqualTo(MarginMode.ISOLATED);
                    });
            assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.Applied.class)).isFalse();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 없으면 거래소 요청을 한다.")
        void ensureMarginMode_whenCacheEmpty_publishesIORequest() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());

            configurer.ensureMarginMode(new MarginModeChangeEvent.EnsureCommand("BTC-USDT", MarginMode.ISOLATED), null);

            var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeEvent.IORequested.class);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.wantTo()).isEqualTo(MarginMode.ISOLATED);
                    });
        }

        @Test
        @DisplayName("wantTo가 null이면 실패 이벤트가 발행된다.")
        void ensureMarginMode_whenDesiredIsNull_publishesFailed() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());

            configurer.ensureMarginMode(new MarginModeChangeEvent.EnsureCommand("BTC-USDT", null), null);

            var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeEvent.Failed.class);
            assertThat(occurred).isPresent();
            assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.IORequested.class)).isFalse();
        }
    }
}