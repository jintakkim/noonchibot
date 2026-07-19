package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.derivative.*;
import com.hotak.noonchibot.core.event.EventSubscriberAssert;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeIORequestedEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeIORequestedEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeIORequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DerivativeAccountCommandExecutorTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private final TradingPairSymbolRegistry symbolRegistry = BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY;
    private DerivativeAccountCommandExecutor executor;


    @BeforeEach
    void setup() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        restAssistant.setExchangeErrorClassifier(new BinanceExchangeErrorClassifier(new ObjectMapper()));
        executor = new DerivativeAccountCommandExecutor(
                restAssistant,
                symbolRegistry,
                eventPublisher,
                eventSubscriber
        );
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
                        executor.positionModeChangeHandler.onEvent(new PositionModeChangeIORequestedEvent(PositionMode.HEDGE));

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
                        executor.positionModeChangeHandler.onEvent(new PositionModeChangeIORequestedEvent(PositionMode.HEDGE));
                        assertThat(eventPublisher.hasEventOfType(PositionModeChangeEvent.Applied.class)).isTrue();
                    }
            );
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
                        executor.leverageChangeHandler.onEvent(new LeverageChangeIORequestedEvent("BTC-USDT", 4));
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
        @DisplayName("레버리지 변경 실패시 예외를 전파하지 않고 Applied 이벤트도 발행하지 않는다")
        void leverageChangeFailureOnlyLogs() {
            runWith(
                    BinanceDerivativeFixture.leverageChangeFailure("BTCUSDT", 500),
                    () -> {
                        executor.leverageChangeHandler.onEvent(new LeverageChangeIORequestedEvent("BTC-USDT", 500));
                        assertThat(eventPublisher.hasEventOfType(LeverageChangeEvent.Applied.class)).isFalse();
                    }
            );
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
                        executor.marginModeChangeHandler.onEvent(
                                new MarginModeChangeIORequestedEvent("BTC-USDT", MarginMode.ISOLATED));

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
                        executor.marginModeChangeHandler.onEvent(new MarginModeChangeIORequestedEvent("BTC-USDT", MarginMode.ISOLATED));
                        assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.Applied.class)).isTrue();
                    }
            );
        }

        @Test
        @DisplayName("마진 모드 변경 실패시 예외를 전파하지 않고 Applied 이벤트도 발행하지 않는다")
        void marginModeChangeFailureOnlyLogs() {
            runWith(
                    BinanceDerivativeFixture.marginModeInvalidSymbol("INVALID", "ISOLATED"),
                    () -> {
                        executor.marginModeChangeHandler.onEvent(
                                new MarginModeChangeIORequestedEvent("INVALID", MarginMode.ISOLATED));
                        assertThat(eventPublisher.hasEventOfType(MarginModeChangeEvent.Applied.class)).isFalse();
                    }
            );
        }
    }

    @Nested
    @DisplayName("라이프사이클 테스트")
    class LifecycleTest {
        @Test
        @DisplayName("phase는 DERIVATIVE_INFO_IO이다")
        void phaseIsDerivativeInfoIo() {
            assertThat(executor.phase()).isEqualTo(Phases.DERIVATIVE_ACCOUNT_COMMAND_EXECUTOR_SETUP);
        }

        @Test
        @DisplayName("시작시 시 3개 핸들러가 Concurrent로 구독된다")
        void onStart_subscribesAllHandlersWithConcurrent() {
            executor.onStart();
            EventSubscriberAssert.assertThat(eventSubscriber)
                    .hasSubscriptionCount(3);

            EventSubscriberAssert.assertThat(eventSubscriber)
                    .subscription(PositionModeChangeIORequestedEvent.class)
                    .usesConcurrentPolicy()
                    .hasHandler(executor.positionModeChangeHandler);

            EventSubscriberAssert.assertThat(eventSubscriber)
                    .subscription(LeverageChangeIORequestedEvent.class)
                    .usesConcurrentPolicy()
                    .hasHandler(executor.leverageChangeHandler);

            EventSubscriberAssert.assertThat(eventSubscriber)
                    .subscription(MarginModeChangeIORequestedEvent.class)
                    .usesConcurrentPolicy()
                    .hasHandler(executor.marginModeChangeHandler);
        }

        @Test
        @DisplayName("종료 시 모든 구독이 해제된다")
        void onShutdown_unsubscribesAll() {
            executor.onStart();
            executor.onShutdown();
            EventSubscriberAssert.assertThat(eventSubscriber).hasNoSubscriptions();
        }
    }
}
