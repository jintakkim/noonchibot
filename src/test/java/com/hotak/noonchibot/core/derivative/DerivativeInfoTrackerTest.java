package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DerivativeInfoTrackerTest {

    private DerivativeInfoTracker tracker;
    private TestEventSubscriber eventSubscriber;

    @BeforeEach
    void setup() {
        eventSubscriber = new TestEventSubscriber();
        tracker = new DerivativeInfoTracker(eventSubscriber);
    }

    @Test
    @DisplayName("onStart 시 derivative info 이벤트를 구독한다")
    void onStartSubscribesToEvents() {
        tracker.onStart();

        assertThat(eventSubscriber.isSubscribed(PositionModeChangeEvent.Applied.class)).isTrue();
        assertThat(eventSubscriber.isSubscribed(LeverageChangeEvent.Applied.class)).isTrue();
        assertThat(eventSubscriber.isSubscribed(MarginModeChangeEvent.Applied.class)).isTrue();
    }

    @Test
    @DisplayName("onShutdown 시 구독을 해제한다")
    void onShutdownUnsubscribesFromEvents() {
        tracker.onStart();

        tracker.onShutdown();

        assertThat(eventSubscriber.count()).isZero();
    }

    @Nested
    @DisplayName("포지션 모드")
    class PositionModeTest {
        @Test
        @DisplayName("초기 상태에서는 포지션 모드가 비어있다")
        void initiallyEmpty() {
            assertThat(tracker.findPositionMode()).isEmpty();
        }

        @Test
        @DisplayName("이벤트 수신 시 포지션 모드가 갱신된다")
        void updatedOnEvent() {
            tracker.onPositionModeChanged(new PositionModeChangeEvent.Applied(PositionMode.HEDGE));

            assertThat(tracker.findPositionMode()).hasValue(PositionMode.HEDGE);
        }
    }

    @Nested
    @DisplayName("레버리지")
    class LeverageTest {
        @Test
        @DisplayName("이벤트 수신 시 페어별 레버리지가 저장된다")
        void storedPerPair() {
            tracker.onLeverageChanged(new LeverageChangeEvent.Applied("BTC-USDT", 10));
            tracker.onLeverageChanged(new LeverageChangeEvent.Applied("ETH-USDT", 5));

            assertThat(tracker.findLeverage("BTC-USDT")).hasValue(10);
            assertThat(tracker.findLeverage("ETH-USDT")).hasValue(5);
        }
    }

    @Nested
    @DisplayName("마진 모드")
    class MarginModeTest {
        @Test
        @DisplayName("이벤트 수신 시 페어별 마진 모드가 저장된다")
        void storedPerPair() {
            tracker.onMarginModeChanged(new MarginModeChangeEvent.Applied("BTC-USDT", MarginMode.ISOLATED));
            tracker.onMarginModeChanged(new MarginModeChangeEvent.Applied("ETH-USDT", MarginMode.CROSS));

            assertThat(tracker.findMarginMode("BTC-USDT")).hasValue(MarginMode.ISOLATED);
            assertThat(tracker.findMarginMode("ETH-USDT")).hasValue(MarginMode.CROSS);
        }
    }
}
