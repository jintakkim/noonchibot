package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.exchange.ExchangeOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReconciliationCoordinatorTest {
    private static final Exchange EXCHANGE = Exchange.BINANCE_DERIVATIVE;
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    private OrderReconciliationTaskRepository repository;
    private OrderStatusReader statusReader;
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler scheduler;
    private AtomicReference<OrderReconciliationTask> savedTask;
    private OrderReconciliationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        repository = mock(OrderReconciliationTaskRepository.class);
        statusReader = mock(OrderStatusReader.class);
        eventPublisher = new TestEventPublisher();
        scheduler = new TestTaskScheduler();
        savedTask = new AtomicReference<>();

        when(repository.findByExchangeAndStatusInOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(List.of());
        when(repository.findByExchangeAndClientOrderId(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(OrderReconciliationTask.class))).thenAnswer(invocation -> {
            OrderReconciliationTask task = invocation.getArgument(0);
            savedTask.set(task);
            return task;
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(savedTask.get()));

        coordinator = new OrderReconciliationCoordinator(
                EXCHANGE,
                repository,
                statusReader,
                eventPublisher,
                scheduler,
                new OrderReconciliationConfig(
                        3,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("주문 생성 결과가 불명확하면 작업을 저장하고 즉시 상태 조회를 예약한다")
    void enqueuePersistsAndSchedulesImmediately() {
        coordinator.onStart();

        coordinator.enqueue(failure(ExchangeOperation.ORDER_PLACE, new RuntimeException("시간 초과")));

        assertThat(savedTask.get().getStatus()).isEqualTo(OrderReconciliationStatus.PENDING);
        assertThat(scheduler.onlyScheduledTask().startTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("주문 상태 조회에 성공하면 상태 이벤트를 발행하고 작업을 해결한다")
    void successfulFetchResolvesTask() {
        OrderEvent.StatusReceived status = new OrderEvent.StatusReceived(
                "BTC-USDT",
                "cid-1",
                "eid-1",
                OrderState.OPEN,
                NOW
        );
        when(statusReader.fetch("BTC-USDT", "cid-1")).thenReturn(status);
        coordinator.onStart();
        coordinator.enqueue(failure(ExchangeOperation.ORDER_PLACE, new RuntimeException("시간 초과")));

        scheduler.onlyScheduledTask().task().run();

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class)).isEqualTo(status);
        assertThat(savedTask.get().getStatus()).isEqualTo(OrderReconciliationStatus.RESOLVED);
        assertThat(savedTask.get().getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("주문을 아직 찾지 못하면 실패 이벤트 없이 지수 백오프로 다시 조회한다")
    void orderNotFoundSchedulesRetryWithoutFailureEvent() {
        when(statusReader.fetch("BTC-USDT", "cid-1"))
                .thenThrow(new OrderNotFoundException("거래소 반영 대기"));
        coordinator.onStart();
        coordinator.enqueue(failure(ExchangeOperation.ORDER_PLACE, new RuntimeException("시간 초과")));

        scheduler.onlyScheduledTask().task().run();

        assertThat(savedTask.get().getStatus()).isEqualTo(OrderReconciliationStatus.WAITING_RETRY);
        assertThat(savedTask.get().getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(eventPublisher.getEvents()).isEmpty();
        assertThat(scheduler.scheduledTasks()).hasSize(2);
    }

    @Test
    @DisplayName("상태 조회가 실패하면 조회 실패 이벤트를 발행하고 최대 시도 후 느린 재조정으로 전환한다")
    void fetchFailurePublishesEventAndBecomesUnresolved() {
        coordinator = new OrderReconciliationCoordinator(
                EXCHANGE,
                repository,
                statusReader,
                eventPublisher,
                scheduler,
                new OrderReconciliationConfig(
                        1,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(statusReader.fetch("BTC-USDT", "cid-1"))
                .thenThrow(new IllegalStateException("연결 종료"));
        coordinator.onStart();
        coordinator.enqueue(failure(ExchangeOperation.ORDER_PLACE, new RuntimeException("시간 초과")));

        scheduler.onlyScheduledTask().task().run();

        ExchangeFailureEvent failure = eventPublisher.only(ExchangeFailureEvent.class);
        assertThat(failure.operation()).isEqualTo(ExchangeOperation.ORDER_STATUS_QUERY);
        assertThat(savedTask.get().getStatus()).isEqualTo(OrderReconciliationStatus.UNRESOLVED);
        assertThat(savedTask.get().getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(scheduler.scheduledTasks()).hasSize(2);
    }

    @Test
    @DisplayName("상태 조회 실패 이벤트는 새로운 조정 작업을 만들지 않는다")
    void statusQueryFailureDoesNotCreateTask() {
        coordinator.onStart();

        coordinator.enqueue(failure(ExchangeOperation.ORDER_STATUS_QUERY, new RuntimeException("조회 실패")));

        verify(repository, never()).save(any(OrderReconciliationTask.class));
        assertThat(scheduler.scheduledTasks()).isEmpty();
    }

    @Test
    @DisplayName("시작 시 저장된 미완료 작업을 복원하고 종료 시 예약을 취소한다")
    void lifecycleRestoresAndCancelsPendingTasks() {
        OrderReconciliationTask task = OrderReconciliationTask.from(
                failure(ExchangeOperation.ORDER_PLACE, new RuntimeException("시간 초과")),
                NOW
        );
        when(repository.findByExchangeAndStatusInOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(List.of(task));

        coordinator.onStart();
        coordinator.onShutdown();

        assertThat(scheduler.onlyScheduledTask().isCancelled()).isTrue();
    }

    private ExchangeFailureEvent failure(ExchangeOperation operation, Throwable cause) {
        return new ExchangeFailureEvent(
                EXCHANGE,
                operation,
                "BTC-USDT",
                "cid-1",
                null,
                "strategy-1",
                "group-1",
                cause,
                NOW
        );
    }
}
