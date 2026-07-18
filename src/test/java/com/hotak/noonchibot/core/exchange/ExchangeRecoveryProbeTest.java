package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.connector.ExchangeAuthenticationException;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeOperationSucceededEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderNotFoundException;
import com.hotak.noonchibot.core.order.OrderReconciliationTask;
import com.hotak.noonchibot.core.order.OrderReconciliationTaskRepository;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderStatusReader;
import com.hotak.noonchibot.core.order.OrderTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeRecoveryProbeTest {
    private static final Exchange EXCHANGE = Exchange.BINANCE_DERIVATIVE;
    private static final Instant QUARANTINED_AT = Instant.parse("2026-07-17T00:00:00Z");
    private static final Instant PROBE_AT = QUARANTINED_AT.plusSeconds(61);

    private ExchangeEligibilityRegistry registry;
    private OrderReconciliationTaskRepository repository;
    private OrderStatusReader statusReader;
    private TestEventPublisher publisher;
    private TestTaskScheduler scheduler;
    private OrderTracker orderTracker;

    @BeforeEach
    void setUp() {
        registry = new ExchangeEligibilityRegistry(3, Clock.fixed(QUARANTINED_AT, ZoneOffset.UTC));
        repository = mock(OrderReconciliationTaskRepository.class);
        statusReader = mock(OrderStatusReader.class);
        publisher = new TestEventPublisher();
        scheduler = new TestTaskScheduler();
        orderTracker = mock(OrderTracker.class);
        when(repository.findFirstByExchangeOrderByUpdatedAtDesc(EXCHANGE)).thenReturn(Optional.empty());
        when(orderTracker.getAllInFlightOrders()).thenReturn(List.of());
    }

    @Test
    @DisplayName("쿨다운 후 인증된 주문 상태 조회가 연속 성공하면 거래소 자격을 복구한다")
    void consecutiveSuccessesRestoreEligibility() {
        OrderReconciliationTask task = reconciliationTask();
        when(repository.findFirstByExchangeOrderByUpdatedAtDesc(EXCHANGE)).thenReturn(Optional.of(task));
        when(repository.save(task)).thenReturn(task);
        OrderEvent.StatusReceived status = status();
        when(statusReader.fetch("BTC-USDT", "cid-1")).thenReturn(status);
        registry.quarantine(EXCHANGE, new RuntimeException("주문 결과 불명확"));
        ExchangeRecoveryProbe probe = probe(2);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();
        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.PROBING);
        scheduler.onlyScheduledTask().task().run();

        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.ELIGIBLE);
        assertThat(publisher.countEventsOfType(OrderEvent.StatusReceived.class)).isEqualTo(2);
        assertThat(publisher.countEventsOfType(ExchangeOperationSucceededEvent.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("주문 없음 응답은 엔드포인트가 정상인 것으로 판단한다")
    void orderNotFoundCountsAsHealthyProbe() {
        when(repository.findFirstByExchangeOrderByUpdatedAtDesc(EXCHANGE))
                .thenReturn(Optional.of(reconciliationTask()));
        when(statusReader.fetch("BTC-USDT", "cid-1"))
                .thenThrow(new OrderNotFoundException("주문 없음"));
        registry.quarantine(EXCHANGE, new RuntimeException("실패"));
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();

        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.ELIGIBLE);
        assertThat(publisher.countEventsOfType(ExchangeFailureEvent.class)).isZero();
        assertThat(publisher.countEventsOfType(ExchangeOperationSucceededEvent.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("상태 조회 전송 실패는 다시 격리하고 실패 이벤트를 발행한다")
    void transportFailureQuarantinesAgain() {
        when(repository.findFirstByExchangeOrderByUpdatedAtDesc(EXCHANGE))
                .thenReturn(Optional.of(reconciliationTask()));
        when(statusReader.fetch("BTC-USDT", "cid-1"))
                .thenThrow(new IllegalStateException("연결 종료"));
        registry.quarantine(EXCHANGE, new RuntimeException("실패"));
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();

        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.QUARANTINED);
        assertThat(publisher.only(ExchangeFailureEvent.class).operation())
                .isEqualTo(ExchangeOperation.ORDER_STATUS_QUERY);
    }

    @Test
    @DisplayName("인증 실패는 정상 응답으로 간주하지 않는다")
    void authenticationFailureDoesNotRestoreEligibility() {
        when(repository.findFirstByExchangeOrderByUpdatedAtDesc(EXCHANGE))
                .thenReturn(Optional.of(reconciliationTask()));
        when(statusReader.fetch("BTC-USDT", "cid-1"))
                .thenThrow(new ExchangeAuthenticationException(HttpStatus.UNAUTHORIZED, "키 만료"));
        registry.quarantine(EXCHANGE, new RuntimeException("실패"));
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();

        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.QUARANTINED);
        assertThat(publisher.countEventsOfType(ExchangeFailureEvent.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("수동 차단된 거래소는 자동 복구를 시도하지 않는다")
    void manualBlockNeverAutoRecovers() {
        registry.manualBlock(EXCHANGE, new RuntimeException("권한 없음"));
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();

        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.MANUAL_BLOCKED);
        verify(statusReader, never()).fetch("BTC-USDT", "cid-1");
    }

    @Test
    @DisplayName("조정 작업이 없으면 가장 최근 인플라이트 주문으로 복구를 확인한다")
    void fallsBackToLatestInFlightOrder() {
        InFlightOrder older = mock(InFlightOrder.class);
        when(older.getCreationTimestamp()).thenReturn(QUARANTINED_AT.minusSeconds(10));
        InFlightOrder latest = mock(InFlightOrder.class);
        when(latest.getCreationTimestamp()).thenReturn(QUARANTINED_AT);
        when(latest.getTradingPair()).thenReturn("ETH-USDT");
        when(latest.getClientOrderId()).thenReturn("cid-latest");
        when(orderTracker.getAllInFlightOrders()).thenReturn(List.of(older, latest));
        when(statusReader.fetch("ETH-USDT", "cid-latest")).thenReturn(new OrderEvent.StatusReceived(
                "ETH-USDT",
                "cid-latest",
                "eid-latest",
                OrderState.OPEN,
                PROBE_AT
        ));
        registry.quarantine(EXCHANGE, new RuntimeException("취소 실패"));
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();

        verify(statusReader).fetch("ETH-USDT", "cid-latest");
        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.ELIGIBLE);
    }

    @Test
    @DisplayName("확인할 기존 주문이 없으면 격리 상태를 유지한다")
    void noProbeTargetKeepsQuarantined() {
        registry.quarantine(EXCHANGE, new RuntimeException("실패"));
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        scheduler.onlyScheduledTask().task().run();

        assertThat(registry.status(EXCHANGE)).isEqualTo(ExchangeEligibilityStatus.QUARANTINED);
        verify(statusReader, never()).fetch("BTC-USDT", "cid-1");
    }

    @Test
    @DisplayName("종료하면 주기 복구 작업을 취소한다")
    void shutdownCancelsScheduledProbe() {
        ExchangeRecoveryProbe probe = probe(1);
        probe.onStart();

        probe.onShutdown();

        assertThat(scheduler.onlyScheduledTask().isCancelled()).isTrue();
    }

    private ExchangeRecoveryProbe probe(int requiredSuccesses) {
        return new ExchangeRecoveryProbe(
                EXCHANGE,
                registry,
                repository,
                statusReader,
                publisher,
                scheduler,
                orderTracker,
                new ExchangeRecoveryProbeConfig(
                        Duration.ofMinutes(1),
                        Duration.ofSeconds(30),
                        requiredSuccesses
                ),
                Clock.fixed(PROBE_AT, ZoneOffset.UTC)
        );
    }

    private OrderReconciliationTask reconciliationTask() {
        return OrderReconciliationTask.from(new ExchangeFailureEvent(
                EXCHANGE,
                ExchangeOperation.ORDER_PLACE,
                "BTC-USDT",
                "cid-1",
                null,
                "strategy-1",
                "group-1",
                new RuntimeException("응답 없음"),
                QUARANTINED_AT
        ), QUARANTINED_AT);
    }

    private OrderEvent.StatusReceived status() {
        return new OrderEvent.StatusReceived(
                "BTC-USDT",
                "cid-1",
                "eid-1",
                OrderState.OPEN,
                PROBE_AT
        );
    }
}
