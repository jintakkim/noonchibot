package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.connector.ExchangeAuthenticationException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeOperationSucceededEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.InvalidOrderRejectedException;
import com.hotak.noonchibot.core.order.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.ELIGIBLE;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.MANUAL_BLOCKED;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.QUARANTINED;
import static org.assertj.core.api.Assertions.assertThat;

class ExchangeFailureCoordinatorTest {
    private static final Exchange EXCHANGE = Exchange.BINANCE_DERIVATIVE;
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-17T00:00:00Z");

    private TestEventSubscriber subscriber;
    private TestEventPublisher publisher;
    private ExchangeEligibilityRegistry registry;
    private List<ExchangeFailureEvent> reconciliationEvents;
    private ExchangeFailureCoordinator coordinator;

    @BeforeEach
    void setUp() {
        subscriber = new TestEventSubscriber();
        publisher = new TestEventPublisher();
        registry = new ExchangeEligibilityRegistry();
        reconciliationEvents = new ArrayList<>();
        coordinator = new ExchangeFailureCoordinator(
                subscriber,
                new DefaultExchangeFailurePolicy(),
                registry,
                reconciliationEvents::add,
                publisher
        );
    }

    @Test
    @DisplayName("시작 시 거래소 실패와 성공 이벤트를 순차 정책으로 구독한다")
    void onStart_subscribesResultEventsSequentially() {
        coordinator.onStart();

        assertThat(subscriber.countFor(ExchangeFailureEvent.class)).isOne();
        assertThat(subscriber.countFor(ExchangeOperationSucceededEvent.class)).isOne();
        assertThat(subscriber.getSubscriptionsFor(ExchangeFailureEvent.class).getFirst().policy())
                .isInstanceOf(ExecutionPolicy.Sequential.class);
        assertThat(subscriber.getSubscriptionsFor(ExchangeOperationSucceededEvent.class).getFirst().policy())
                .isInstanceOf(ExecutionPolicy.Sequential.class);
    }

    @Test
    @DisplayName("종료 시 등록한 거래소 요청 결과 구독을 해제한다")
    void onShutdown_closesSubscriptions() {
        coordinator.onStart();

        coordinator.onShutdown();

        assertThat(subscriber.count()).isZero();
    }

    @Test
    @DisplayName("확정된 주문 등록 거절은 REJECTED 주문 상태로 발행한다")
    void confirmedPlaceRejection_publishesRejectedStatus() {
        Throwable cause = new InvalidOrderRejectedException(new IllegalArgumentException("invalid quantity"));

        coordinator.handleFailure(failure(ExchangeOperation.ORDER_PLACE, cause));

        OrderEvent.StatusReceived status = publisher.only(OrderEvent.StatusReceived.class);
        assertThat(status.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(status.clientOrderId()).isEqualTo("client-1");
        assertThat(status.orderState()).isEqualTo(OrderState.REJECTED);
        assertThat(status.timestamp()).isEqualTo(OCCURRED_AT);
        assertThat(registry.status(EXCHANGE)).isEqualTo(ELIGIBLE);
        assertThat(reconciliationEvents).isEmpty();
    }

    @Test
    @DisplayName("불명확한 주문 등록 실패는 즉시 거래소를 격리하고 재조정 작업에 전달한다")
    void ambiguousPlaceFailure_quarantinesAndEnqueuesReconciliation() {
        ExchangeFailureEvent event = failure(
                ExchangeOperation.ORDER_PLACE,
                new ExchangeTransientException(HttpStatus.INTERNAL_SERVER_ERROR, "timeout")
        );

        coordinator.handleFailure(event);

        assertThat(registry.status(EXCHANGE)).isEqualTo(QUARANTINED);
        assertThat(reconciliationEvents).containsExactly(event);
        assertThat(publisher.totalCount()).isZero();
    }

    @Test
    @DisplayName("주문 등록 외 일시 실패는 누적하여 임계치에서 거래소를 격리한다")
    void nonPlaceTransientFailures_areCounted() {
        for (int i = 0; i < ExchangeEligibilityRegistry.DEFAULT_FAILURE_THRESHOLD; i++) {
            coordinator.handleFailure(failure(
                    ExchangeOperation.ORDER_STATUS_QUERY,
                    new ExchangeTransientException(HttpStatus.INTERNAL_SERVER_ERROR, "timeout-" + i)
            ));
        }

        assertThat(registry.status(EXCHANGE)).isEqualTo(QUARANTINED);
        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures())
                .isEqualTo(ExchangeEligibilityRegistry.DEFAULT_FAILURE_THRESHOLD);
        assertThat(reconciliationEvents).isEmpty();
    }

    @Test
    @DisplayName("인증 실패는 횟수와 관계없이 거래소를 수동 차단한다")
    void authenticationFailure_manuallyBlocksExchange() {
        coordinator.handleFailure(failure(
                ExchangeOperation.ORDER_STATUS_QUERY,
                new ExchangeAuthenticationException(HttpStatus.UNAUTHORIZED, "invalid key")
        ));

        assertThat(registry.status(EXCHANGE)).isEqualTo(MANUAL_BLOCKED);
    }

    @Test
    @DisplayName("정상 거래소 요청 이벤트는 연속 실패 횟수를 초기화한다")
    void successfulOperation_resetsFailureCount() {
        coordinator.handleFailure(failure(
                ExchangeOperation.ORDER_CANCEL,
                new ExchangeTransientException(HttpStatus.INTERNAL_SERVER_ERROR, "timeout")
        ));

        coordinator.handleSuccess(new ExchangeOperationSucceededEvent(
                EXCHANGE,
                ExchangeOperation.ORDER_CANCEL,
                OCCURRED_AT.plusSeconds(1)
        ));

        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures()).isZero();
        assertThat(registry.status(EXCHANGE)).isEqualTo(ELIGIBLE);
    }

    private ExchangeFailureEvent failure(ExchangeOperation operation, Throwable cause) {
        return new ExchangeFailureEvent(
                EXCHANGE,
                operation,
                "BTC-USDT",
                "client-1",
                null,
                "strategy-1",
                "group-1",
                cause,
                OCCURRED_AT
        );
    }
}
