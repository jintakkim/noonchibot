package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.ReconcileStatus;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeIORequestedEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeIORequestedEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeIORequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class DerivativeAccountReconcilerTest {
    private TestEventPublisher eventPublisher;
    private DerivativeAccountTracker tracker;
    private DerivativeAccountReconciler reconciler;

    @BeforeEach
    void setup() {
        eventPublisher = new TestEventPublisher();
        tracker = Mockito.mock(DerivativeAccountTracker.class);
        reconciler = new DerivativeAccountReconciler(tracker, eventPublisher);
    }

    @Nested
    @DisplayName("PositionMode 테스트")
    class PositionModeContract {
        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 같으면 IN_SYNC를 반환한다.")
        void reconcilePositionMode_whenCacheMatchesDesired_skipsRequest() {
            when(tracker.findPositionMode()).thenReturn(Optional.of(PositionMode.HEDGE));

            ReconcileStatus result = reconciler.reconcilePositionMode(PositionMode.HEDGE);

            assertThat(result).isEqualTo(ReconcileStatus.IN_SYNC);
            assertThat(eventPublisher.totalCount()).isZero();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 다르면 거래소 요청을 한다.")
        void reconcilePositionMode_whenCacheDiffers_publishesIORequest() {
            when(tracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
            ReconcileStatus result = reconciler.reconcilePositionMode(PositionMode.HEDGE);
            var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeIORequestedEvent.class);

            assertThat(result).isEqualTo(ReconcileStatus.APPLYING);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> assertThat(event.wantTo()).isEqualTo(PositionMode.HEDGE));
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 없으면 거래소 요청을 한다.")
        void reconcilePositionMode_whenCacheEmpty_publishesIORequest() {
            when(tracker.findPositionMode()).thenReturn(Optional.empty());
            ReconcileStatus result = reconciler.reconcilePositionMode(PositionMode.HEDGE);
            var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeIORequestedEvent.class);

            assertThat(result).isEqualTo(ReconcileStatus.APPLYING);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> assertThat(event.wantTo()).isEqualTo(PositionMode.HEDGE));
        }


        @Test
        @DisplayName("wantTo가 null이면 예외를 던진다.")
        void reconcilePositionMode_whenDesiredIsNull_throws() {
            when(tracker.findPositionMode()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciler.reconcilePositionMode(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Leverage 테스트")
    class LeverageContract {
        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 같으면 IN_SYNC를 반환한다.")
        void reconcileLeverage_whenCacheMatchesDesired_returnsInSync() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.of(4));

            ReconcileStatus result = reconciler.reconcileLeverage("BTC-USDT", 4);

            assertThat(result).isEqualTo(ReconcileStatus.IN_SYNC);
            assertThat(eventPublisher.totalCount()).isZero();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 다르면 거래소 요청을 한다.")
        void reconcileLeverage_whenCacheDiffersFromDesired_publishesIORequest() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.of(10));

            ReconcileStatus result = reconciler.reconcileLeverage("BTC-USDT", 4);

            var occurred = eventPublisher.getFirstEventOfType(LeverageChangeIORequestedEvent.class);
            assertThat(result).isEqualTo(ReconcileStatus.APPLYING);
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
        void reconcileLeverage_whenCacheEmpty_publishesIORequest() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.empty());

            ReconcileStatus result = reconciler.reconcileLeverage("BTC-USDT", 4);

            var occurred = eventPublisher.getFirstEventOfType(LeverageChangeIORequestedEvent.class);
            assertThat(result).isEqualTo(ReconcileStatus.APPLYING);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.wantTo()).isEqualTo(4);
                    });
        }

        @Test
        @DisplayName("wantTo가 음수면 예외를 던진다.")
        void reconcileLeverage_whenWantToIsNegative_throws() {
            when(tracker.findLeverage("BTC-USDT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciler.reconcileLeverage("BTC-USDT", -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(eventPublisher.totalCount()).isZero();
        }
    }


    @Nested
    @DisplayName("MarginMode 테스트")
    class MarginModeContract {

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 같으면 IN_SYNC를 반환한다.")
        void reconcileMarginMode_whenCacheMatchesDesired_returnsInSync() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.ISOLATED));

            ReconcileStatus result = reconciler.reconcileMarginMode("BTC-USDT", MarginMode.ISOLATED);

            assertThat(result).isEqualTo(ReconcileStatus.IN_SYNC);
            assertThat(eventPublisher.totalCount()).isZero();
        }

        @Test
        @DisplayName("Tracker에 캐시 값이 있고 목표로 하는 값과 다르면 거래소 요청을 한다.")
        void reconcileMarginMode_whenCacheDiffers_publishesIORequest() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.CROSS));

            ReconcileStatus result = reconciler.reconcileMarginMode("BTC-USDT", MarginMode.ISOLATED);

            var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeIORequestedEvent.class);
            assertThat(result).isEqualTo(ReconcileStatus.APPLYING);
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
        void reconcileMarginMode_whenCacheEmpty_publishesIORequest() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());

            ReconcileStatus result = reconciler.reconcileMarginMode("BTC-USDT", MarginMode.ISOLATED);

            var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeIORequestedEvent.class);
            assertThat(result).isEqualTo(ReconcileStatus.APPLYING);
            assertThat(occurred)
                    .isPresent()
                    .hasValueSatisfying(event -> {
                        assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                        assertThat(event.wantTo()).isEqualTo(MarginMode.ISOLATED);
                    });
        }

        @Test
        @DisplayName("wantTo가 null이면 예외를 던진다.")
        void reconcileMarginMode_whenDesiredIsNull_throws() {
            when(tracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciler.reconcileMarginMode("BTC-USDT", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(eventPublisher.totalCount()).isZero();
        }
    }
}
