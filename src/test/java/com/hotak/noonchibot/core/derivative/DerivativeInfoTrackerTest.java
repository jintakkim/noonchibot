package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DerivativeInfoTrackerTest {

    private DerivativeInfoTracker tracker;
    private ExchangeEventSubscriber eventSubscriber;

    @BeforeEach
    void setup() {
        eventSubscriber = mock(ExchangeEventSubscriber.class);
        tracker = new DerivativeInfoTracker(eventSubscriber);
    }

    @Test
    @DisplayName("start 시 모든 이벤트에 구독한다")
    void startSubscribesToAllEvents() {
        tracker.start();

        verify(eventSubscriber).subscribe(eq(PositionUpdateEvent.class), any());
        verify(eventSubscriber).subscribe(eq(LeverageChangedEvent.class), any());
        verify(eventSubscriber).subscribe(eq(MarginModeChangedEvent.class), any());
        verify(eventSubscriber).subscribe(eq(PositionModeChangedEvent.class), any());
    }

    @Test
    @DisplayName("stop 시 running=false이고 모든 이벤트 구독을 해제한다")
    void stopUnsubscribesFromAllEvents() {
        tracker.start();
        tracker.shutdown();

        verify(eventSubscriber).unsubscribe(eq(PositionUpdateEvent.class), any());
        verify(eventSubscriber).unsubscribe(eq(LeverageChangedEvent.class), any());
        verify(eventSubscriber).unsubscribe(eq(MarginModeChangedEvent.class), any());
        verify(eventSubscriber).unsubscribe(eq(PositionModeChangedEvent.class), any());
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
            tracker.onPositionModeChanged(new PositionModeChangedEvent(PositionMode.HEDGE));

            assertThat(tracker.findPositionMode()).hasValue(PositionMode.HEDGE);
        }

        @Test
        @DisplayName("여러 번 이벤트 수신 시 최신 값으로 덮어쓴다")
        void overwrittenByLatestEvent() {
            tracker.onPositionModeChanged(new PositionModeChangedEvent(PositionMode.ONEWAY));
            tracker.onPositionModeChanged(new PositionModeChangedEvent(PositionMode.HEDGE));

            assertThat(tracker.findPositionMode()).hasValue(PositionMode.HEDGE);
        }
    }

    @Nested
    @DisplayName("레버리지")
    class LeverageTest {
        @Test
        @DisplayName("초기 상태에서는 레버리지가 비어있다")
        void initiallyEmpty() {
            assertThat(tracker.findLeverage("BTC-USDT")).isEmpty();
        }

        @Test
        @DisplayName("이벤트 수신 시 페어별 레버리지가 저장된다")
        void storedPerPair() {
            tracker.onLeverageChanged(new LeverageChangedEvent("BTC-USDT", 10));
            tracker.onLeverageChanged(new LeverageChangedEvent("ETH-USDT", 5));

            assertThat(tracker.findLeverage("BTC-USDT")).hasValue(10);
            assertThat(tracker.findLeverage("ETH-USDT")).hasValue(5);
        }

        @Test
        @DisplayName("같은 페어에 여러 번 이벤트 수신 시 최신 값으로 덮어쓴다")
        void overwrittenByLatestEvent() {
            tracker.onLeverageChanged(new LeverageChangedEvent("BTC-USDT", 10));
            tracker.onLeverageChanged(new LeverageChangedEvent("BTC-USDT", 20));

            assertThat(tracker.findLeverage("BTC-USDT")).hasValue(20);
        }

        @Test
        @DisplayName("저장되지 않은 페어 조회 시 빈 Optional을 반환한다")
        void emptyForUnknownPair() {
            tracker.onLeverageChanged(new LeverageChangedEvent("BTC-USDT", 10));

            assertThat(tracker.findLeverage("UNKNOWN-PAIR")).isEmpty();
        }
    }

    @Nested
    @DisplayName("마진 모드")
    class MarginModeTest {

        @Test
        @DisplayName("초기 상태에서는 마진 모드가 비어있다")
        void initiallyEmpty() {
            assertThat(tracker.findMarginMode("BTC-USDT")).isEmpty();
        }

        @Test
        @DisplayName("이벤트 수신 시 페어별 마진 모드가 저장된다")
        void storedPerPair() {
            tracker.onMarginModeChanged(new MarginModeChangedEvent("BTC-USDT", MarginMode.ISOLATED));
            tracker.onMarginModeChanged(new MarginModeChangedEvent("ETH-USDT", MarginMode.CROSS));

            assertThat(tracker.findMarginMode("BTC-USDT")).hasValue(MarginMode.ISOLATED);
            assertThat(tracker.findMarginMode("ETH-USDT")).hasValue(MarginMode.CROSS);
        }

        @Test
        @DisplayName("같은 페어에 여러 번 이벤트 수신 시 최신 값으로 덮어쓴다")
        void overwrittenByLatestEvent() {
            tracker.onMarginModeChanged(new MarginModeChangedEvent("BTC-USDT", MarginMode.CROSS));
            tracker.onMarginModeChanged(new MarginModeChangedEvent("BTC-USDT", MarginMode.ISOLATED));

            assertThat(tracker.findMarginMode("BTC-USDT")).hasValue(MarginMode.ISOLATED);
        }
    }

    @Nested
    @DisplayName("포지션")
    class PositionTest {

        @Test
        @DisplayName("초기 상태에서는 포지션이 비어있다")
        void initiallyEmpty() {
            assertThat(tracker.findPosition("BTC-USDT", PositionSide.LONG)).isEmpty();
        }

        @Test
        @DisplayName("신규 포지션 이벤트 수신 시 새 Position이 저장된다")
        void createsNewPosition() {
            PositionUpdateEvent event = new PositionUpdateEvent(
                    "BTC-USDT",
                    PositionSide.LONG,
                    new BigDecimal("0.5"),
                    new BigDecimal("45000"),
                    new BigDecimal("100"),
                    Instant.now()
            );

            tracker.onPositionUpdated(event);

            Optional<Position> position = tracker.findPosition("BTC-USDT", PositionSide.LONG);
            assertThat(position).isPresent();
            assertThat(position.get().getTradingPair()).isEqualTo("BTC-USDT");
            assertThat(position.get().getPositionSide()).isEqualTo(PositionSide.LONG);
            assertThat(position.get().getAmount()).isEqualByComparingTo("0.5");
            assertThat(position.get().getEntryPrice()).isEqualByComparingTo("45000");
            assertThat(position.get().getUnrealizedPnl()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("기존 포지션 이벤트 수신 시 값이 업데이트된다")
        void updatesExistingPosition() {
            // 최초 포지션
            tracker.onPositionUpdated(new PositionUpdateEvent(
                    "BTC-USDT", PositionSide.LONG,
                    new BigDecimal("0.5"), new BigDecimal("45000"), new BigDecimal("100"),
                    Instant.now()
            ));
            // 업데이트
            tracker.onPositionUpdated(new PositionUpdateEvent(
                    "BTC-USDT", PositionSide.LONG,
                    new BigDecimal("1.0"), new BigDecimal("46000"), new BigDecimal("200"),
                    Instant.now()
            ));

            Position position = tracker.findPosition("BTC-USDT", PositionSide.LONG).orElseThrow();
            assertThat(position.getAmount()).isEqualByComparingTo("1.0");
            assertThat(position.getEntryPrice()).isEqualByComparingTo("46000");
            assertThat(position.getUnrealizedPnl()).isEqualByComparingTo("200");
        }

        @Test
        @DisplayName("amount가 0인 이벤트 수신 시 포지션이 제거된다")
        void removesPositionOnZeroAmount() {
            // 포지션 생성
            tracker.onPositionUpdated(new PositionUpdateEvent(
                    "BTC-USDT", PositionSide.LONG,
                    new BigDecimal("0.5"), new BigDecimal("45000"), new BigDecimal("100"),
                    Instant.now()
            ));
            assertThat(tracker.findPosition("BTC-USDT", PositionSide.LONG)).isPresent();

            // 청산
            tracker.onPositionUpdated(new PositionUpdateEvent(
                    "BTC-USDT", PositionSide.LONG,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    Instant.now()
            ));
            assertThat(tracker.findPosition("BTC-USDT", PositionSide.LONG)).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 포지션에 대한 amount=0 이벤트는 무시된다")
        void ignoresZeroAmountForNonexistentPosition() {
            assertThatCode(() -> tracker.onPositionUpdated(new PositionUpdateEvent(
                    "BTC-USDT", PositionSide.LONG,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    Instant.now()
            ))).doesNotThrowAnyException();
            assertThat(tracker.findPosition("BTC-USDT", PositionSide.LONG)).isEmpty();
        }
    }
}
