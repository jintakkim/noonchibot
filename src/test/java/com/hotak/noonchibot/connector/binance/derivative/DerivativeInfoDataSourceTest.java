package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.derivative.*;
import com.hotak.noonchibot.core.event.EventSubscriberAssert;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerivativeInfoDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private final TradingPairSymbolRegistry symbolRegistry = BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY;
    private DerivativeInfoDataSource client;


    @BeforeEach
    void setup() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        client = new DerivativeInfoDataSource(restAssistant, symbolRegistry, eventPublisher, eventSubscriber);
    }


    @Nested
    @DisplayName("포지션 모드 변경 요청 테스트")
    class PositionModeChangeRequestTest {
        @Test
        @DisplayName("포지션 모드 변경 성공시 Applied 이벤트 발행")
        void positionModeChangeSuccessPublishesEvent() {
            runWith(
                    BinanceDerivativeFixture.positionModeChangeSuccess(true),
                    () -> {
                        client.positionModeChangeHandler.onEvent(new PositionModeChangeEvent.IORequested(PositionMode.HEDGE));

                        var occurred = eventPublisher.getFirstEventOfType(PositionModeChangeEvent.Applied.class);
                        assertThat(occurred)
                                .isPresent()
                                .hasValueSatisfying(event -> assertThat(event.changedTo()).isEqualTo(PositionMode.HEDGE));
                    }
            );
        }

        @Test
        @DisplayName("이미 같은 포지션 모드라도 정상 처리되어 Applied 이벤트 발행")
        void positionModeNoNeedToChangeIsTreatedAsSuccess() {
            runWith(
                    BinanceDerivativeFixture.positionModeNoNeedToChange(true),
                    () -> {
                        client.positionModeChangeHandler.onEvent(new PositionModeChangeEvent.IORequested(PositionMode.HEDGE));
                        assertThat(eventPublisher.hasEventOfType(PositionModeChangeEvent.Applied.class)).isTrue();
                    }
            );
        }

        @Test
        @DisplayName("실패시 이벤드 발행")
        void failureOccursFailedEvent() {
            client.positionModeChangeHandler.onFailure(
                    new PositionModeChangeEvent.IORequested(PositionMode.HEDGE),
                    new IllegalStateException("position mode change failed")
            );
            assertThat(eventPublisher.hasEventOfType(PositionModeChangeEvent.Failed.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("레버리지 변경 요청 테스트")
    class LeverageChangeRequestTest {
        @Test
        @DisplayName("레버리지 변경 요청 성공시 이벤트 발행")
        void leverageChangeSuccessPublishesEvent() {
            runWith(
                    BinanceDerivativeFixture.leverageChangeSuccess("BTCUSDT", 4, 12000000),
                    () -> {
                        client.leverageChangeHandler.onEvent(new LeverageChangeEvent.IORequested("BTC-USDT", 4));
                        var occurred = eventPublisher.getFirstEventOfType(LeverageChangeEvent.Applied.class);
                        assertThat(occurred)
                                .isPresent()
                                .hasValueSatisfying(event -> {
                                    assertThat(event.changedTo()).isEqualTo(4);
                                    assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                                });
                    }
            );
        }

        @Test
        @DisplayName("불가능한 레버리지로 레버리지 변경 요청 실패시 이벤트 발행")
        void leverageChangeFailurePublishesEvent() {
            runWith(
                    BinanceDerivativeFixture.leverageChangeFailure("BTCUSDT", 500),
                    () -> {
                        assertThatThrownBy(() ->
                        client.leverageChangeHandler.onEvent(new LeverageChangeEvent.IORequested("BTC-USDT", 500)))
                                .isInstanceOf(ExchangeApiException.class);
                        assertThat(eventPublisher.hasEventOfType(LeverageChangeEvent.Applied.class)).isFalse();
                    }
            );
        }

        @Test
        @DisplayName("실패시 이벤드 발행")
        void failureOccursFailedEvent() {
            client.leverageChangeHandler.onFailure(
                    new LeverageChangeEvent.IORequested("BTCUSDT", 500),
                    new IllegalStateException("leverage change failed")
            );
            assertThat(eventPublisher.hasEventOfType(LeverageChangeEvent.Failed.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("마진 모드 변경 요청 테스트")
    class MarginModeChangeRequestTest {

        @Test
        @DisplayName("마진 모드 변경 성공시 Applied 이벤트 발행")
        void marginModeChangeSuccessPublishesEvent() {
            runWith(
                    BinanceDerivativeFixture.marginModeChangeSuccess("BTCUSDT", "ISOLATED"),
                    () -> {
                        client.marginModeChangeHandler.onEvent(
                                new MarginModeChangeEvent.IORequested("BTC-USDT", MarginMode.ISOLATED));

                        var occurred = eventPublisher.getFirstEventOfType(MarginModeChangeEvent.Applied.class);
                        assertThat(occurred)
                                .isPresent()
                                .hasValueSatisfying(event -> {
                                    assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                                    assertThat(event.changedTo()).isEqualTo(MarginMode.ISOLATED);
                                });
                    }
            );
        }

        @Test
        @DisplayName("이미 같은 마진 모드라도 정상 처리되어 Applied 이벤트 발행")
        void marginModeNoNeedToChangeIsTreatedAsSuccess() {
            runWith(
                    BinanceDerivativeFixture.marginModeNoNeedToChange("BTCUSDT", "ISOLATED"),
                    () -> {
                        client.marginModeChangeHandler.onEvent(new MarginModeChangeEvent.IORequested("BTC-USDT", MarginMode.ISOLATED));
                        assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.Applied.class)).isTrue();
                    }
            );
        }

        @Test
        @DisplayName("잘못된 심볼로 마진 모드 변경 시도시 예외 발생")
        void marginModeInvalidSymbolThrows() {
            runWith(
                    BinanceDerivativeFixture.marginModeInvalidSymbol("INVALID", "ISOLATED"),
                    () -> {
                        assertThatThrownBy(() ->
                                client.marginModeChangeHandler.onEvent(new MarginModeChangeEvent.IORequested("INVALID", MarginMode.ISOLATED)))
                                .isInstanceOf(IllegalArgumentException.class);
                        assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.Applied.class)).isFalse();
                    }
            );
        }

        @Test
        @DisplayName("실패시 이벤드 발행")
        void failureOccursFailedEvent() {
            client.marginModeChangeHandler.onFailure(
                    new MarginModeChangeEvent.IORequested("Invalid", MarginMode.ISOLATED),
                    new IllegalStateException("margin mode change failed")
            );
            assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.Failed.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("라이프사이클 테스트")
    class LifecycleTest {
        @Test
        @DisplayName("phase는 DERIVATIVE_INFO_IO이다")
        void phaseIsDerivativeInfoIo() {
            assertThat(client.phase()).isEqualTo(Phases.DERIVATIVE_INFO_DATASOURCE_SETUP);
        }

        @Test
        @DisplayName("시작시 시 3개 핸들러가 Concurrent로 구독된다")
        void onStart_subscribesAllHandlersWithConcurrent() {
            client.onStart();
            EventSubscriberAssert.assertThat(eventSubscriber)
                    .hasSubscriptionCount(3);

            EventSubscriberAssert.assertThat(eventSubscriber)
                    .subscription(PositionModeChangeEvent.IORequested.class)
                    .usesConcurrentPolicy()
                    .hasHandler(client.positionModeChangeHandler);

            EventSubscriberAssert.assertThat(eventSubscriber)
                    .subscription(LeverageChangeEvent.IORequested.class)
                    .usesConcurrentPolicy()
                    .hasHandler(client.leverageChangeHandler);

            EventSubscriberAssert.assertThat(eventSubscriber)
                    .subscription(MarginModeChangeEvent.IORequested.class)
                    .usesConcurrentPolicy()
                    .hasHandler(client.marginModeChangeHandler);
        }

        @Test
        @DisplayName("종료 시 모든 구독이 해제된다")
        void onShutdown_unsubscribesAll() {
            client.onStart();
            client.onShutdown();
            EventSubscriberAssert.assertThat(eventSubscriber).hasNoSubscriptions();
        }
    }
}
