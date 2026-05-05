package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.LeverageChangedEvent;
import com.hotak.noonchibot.core.event.MarginModeChangedEvent;
import com.hotak.noonchibot.core.event.PositionModeChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public abstract class AbstractDerivativeAccountConfigurerTest<T extends AbstractDerivativeAccountConfigurer> {

    protected DerivativeInfoTracker mockTracker;
    protected ExchangeEventPublisher mockPublisher;
    protected T configurer;

    @BeforeEach
    void setup() {
        mockTracker = Mockito.mock(DerivativeInfoTracker.class);
        mockPublisher = Mockito.mock(ExchangeEventPublisher.class);
        T real = createConfigurer(mockTracker, mockPublisher);
        configurer = Mockito.spy(real);
    }

    protected abstract T createConfigurer(DerivativeInfoTracker tracker, ExchangeEventPublisher publisher);

    protected abstract String tradingPair();


    private void stubPositionModeApply(CompletableFuture<Void> result) {
        doReturn(result).when(configurer).applyPositionMode(any(PositionMode.class));
    }

    private void stubLeverageApply(CompletableFuture<Void> result) {
        doReturn(result).when(configurer).applyLeverage(anyString(), anyInt());
    }

    private void stubMarginModeApply(CompletableFuture<Void> result) {
        doReturn(result).when(configurer).applyMarginMode(anyString(), any(MarginMode.class));
    }

    @Nested
    @DisplayName("PositionMode 테스트")
    class PositionModeContract {

        @Test
        @DisplayName("정상 변경 시 이벤트가 발행된다")
        void publishesEventOnSuccess() {
            when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
            stubPositionModeApply(CompletableFuture.completedFuture(null));

            configurer.ensurePositionMode(PositionMode.HEDGE).join();

            verify(configurer).applyPositionMode(PositionMode.HEDGE);
            verify(mockPublisher).publish(any(PositionModeChangedEvent.class));
        }

        @Test
        @DisplayName("Tracker에 같은 mode가 있으면 거래소를 건드리지 않는다")
        void skipsWhenTrackerHasSameMode() {
            when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.HEDGE));

            configurer.ensurePositionMode(PositionMode.HEDGE).join();

            verify(configurer, never()).applyPositionMode(any());
            verify(mockPublisher, never()).publish(any(PositionModeChangedEvent.class));
        }

        @Test
        @DisplayName("거래소 호출 실패 시 예외가 던져지고 이벤트는 발행되지 않는다")
        void doesNotPublishOnFailure() {
            when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
            stubPositionModeApply(CompletableFuture.failedFuture(
                    new IllegalStateException("active position")));

            assertThatThrownBy(() -> configurer.ensurePositionMode(PositionMode.HEDGE).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            verify(mockPublisher, never()).publish(any(PositionModeChangedEvent.class));
        }

        @Test
        @DisplayName("desired가 null이면 IllegalArgumentException")
        void throwsOnNullDesired() {
            assertThatThrownBy(() -> configurer.ensurePositionMode(null).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            verify(configurer, never()).applyPositionMode(any());
        }
    }

    @Nested
    @DisplayName("Leverage 테스트")
    class LeverageContract {

        @Test
        @DisplayName("정상 변경 시 이벤트가 발행된다")
        void publishesEventOnSuccess() {
            when(mockTracker.findLeverage(tradingPair())).thenReturn(Optional.of(5));
            stubLeverageApply(CompletableFuture.completedFuture(null));

            configurer.ensureLeverage(tradingPair(), 10).join();

            verify(configurer).applyLeverage(tradingPair(), 10);

            ArgumentCaptor<LeverageChangedEvent> eventCaptor =
                    ArgumentCaptor.forClass(LeverageChangedEvent.class);
            verify(mockPublisher).publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue().tradingPair()).isEqualTo(tradingPair());
            assertThat(eventCaptor.getValue().leverage()).isEqualTo(10);
        }

        @Test
        @DisplayName("Tracker가 같은 leverage를 갖고 있으면 skip한다")
        void skipsWhenTrackerHasSameLeverage() {
            when(mockTracker.findLeverage(tradingPair())).thenReturn(Optional.of(10));

            configurer.ensureLeverage(tradingPair(), 10).join();

            verify(configurer, never()).applyLeverage(any(), anyInt());
            verify(mockPublisher, never()).publish(any(LeverageChangedEvent.class));
        }

        @Test
        @DisplayName("거래소 호출 실패 시 이벤트는 발행되지 않는다")
        void doesNotPublishOnFailure() {
            when(mockTracker.findLeverage(tradingPair())).thenReturn(Optional.of(5));
            stubLeverageApply(CompletableFuture.failedFuture(
                    new IllegalStateException("invalid leverage")));

            assertThatThrownBy(() -> configurer.ensureLeverage(tradingPair(), 10).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            verify(mockPublisher, never()).publish(any(LeverageChangedEvent.class));
        }

        @Test
        @DisplayName("leverage가 0 이하면 IllegalArgumentException")
        void throwsOnNonPositiveLeverage() {
            assertThatThrownBy(() -> configurer.ensureLeverage(tradingPair(), 0).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> configurer.ensureLeverage(tradingPair(), -5).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);

            verify(configurer, never()).applyLeverage(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("MarginMode 테스트")
    class MarginModeContract {

        @Test
        @DisplayName("정상 변경 시 이벤트가 발행된다")
        void publishesEventOnSuccess() {
            when(mockTracker.findMarginMode(tradingPair())).thenReturn(Optional.of(MarginMode.CROSS));
            stubMarginModeApply(CompletableFuture.completedFuture(null));

            configurer.ensureMarginMode(tradingPair(), MarginMode.ISOLATED).join();

            verify(configurer).applyMarginMode(tradingPair(), MarginMode.ISOLATED);
            verify(mockPublisher).publish(any(MarginModeChangedEvent.class));
        }

        @Test
        @DisplayName("Tracker에 같은 mode가 있으면 거래소를 건드리지 않는다")
        void skipsWhenTrackerHasSameMode() {
            when(mockTracker.findMarginMode(tradingPair())).thenReturn(Optional.of(MarginMode.ISOLATED));

            configurer.ensureMarginMode(tradingPair(), MarginMode.ISOLATED).join();

            verify(configurer, never()).applyMarginMode(any(), any());
            verify(mockPublisher, never()).publish(any(MarginModeChangedEvent.class));
        }

        @Test
        @DisplayName("거래소 호출 실패 시 이벤트는 발행되지 않는다")
        void doesNotPublishOnFailure() {
            when(mockTracker.findMarginMode(tradingPair())).thenReturn(Optional.of(MarginMode.CROSS));
            stubMarginModeApply(CompletableFuture.failedFuture(new IllegalStateException("active position")));

            assertThatThrownBy(() -> configurer.ensureMarginMode(tradingPair(), MarginMode.ISOLATED).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            verify(mockPublisher, never()).publish(any(MarginModeChangedEvent.class));
        }

        @Test
        @DisplayName("desired가 null이면 IllegalArgumentException")
        void throwsOnNullDesired() {
            assertThatThrownBy(() -> configurer.ensureMarginMode(tradingPair(), null).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            verify(configurer, never()).applyMarginMode(any(), any());
        }
    }
}