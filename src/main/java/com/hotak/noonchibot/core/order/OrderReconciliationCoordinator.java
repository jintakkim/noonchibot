package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.exchange.ExchangeOperation;
import com.hotak.noonchibot.core.exchange.OrderReconciliationSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class OrderReconciliationCoordinator implements LifecycleAware, OrderReconciliationSink {
    private static final Set<OrderReconciliationStatus> INCOMPLETE_STATUSES = EnumSet.of(
            OrderReconciliationStatus.PENDING,
            OrderReconciliationStatus.WAITING_RETRY,
            OrderReconciliationStatus.UNRESOLVED
    );

    private final Exchange exchange;
    private final OrderReconciliationTaskRepository repository;
    private final OrderStatusReader orderStatusReader;
    private final EventPublisher eventPublisher;
    private final TaskScheduler taskScheduler;
    private final OrderReconciliationConfig config;
    private final Clock clock;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Object schedulingMonitor = new Object();

    public OrderReconciliationCoordinator(
            Exchange exchange,
            OrderReconciliationTaskRepository repository,
            OrderStatusReader orderStatusReader,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler
    ) {
        this(
                exchange,
                repository,
                orderStatusReader,
                eventPublisher,
                taskScheduler,
                OrderReconciliationConfig.DEFAULT,
                Clock.systemUTC()
        );
    }

    public OrderReconciliationCoordinator(
            Exchange exchange,
            OrderReconciliationTaskRepository repository,
            OrderStatusReader orderStatusReader,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            OrderReconciliationConfig config,
            Clock clock
    ) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.orderStatusReader = Objects.requireNonNull(orderStatusReader, "orderStatusReader");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void enqueue(ExchangeFailureEvent event) {
        Objects.requireNonNull(event, "event");

        // STATUS_QUERY 실패 이벤트가 다시 task를 만드는 순환을 차단한다.
        if (event.operation() != ExchangeOperation.ORDER_PLACE || event.exchange() != exchange) {
            return;
        }

        Instant now = clock.instant();
        OrderReconciliationTask task = repository
                .findByExchangeAndClientOrderId(exchange, event.clientOrderId())
                .orElseGet(() -> saveNewTask(event, now));

        if (started.get() && task.getStatus() != OrderReconciliationStatus.RESOLVED) {
            schedule(task, now);
        }
    }

    private OrderReconciliationTask saveNewTask(ExchangeFailureEvent event, Instant now) {
        try {
            return repository.save(OrderReconciliationTask.from(event, now));
        } catch (DataIntegrityViolationException race) {
            return repository.findByExchangeAndClientOrderId(exchange, event.clientOrderId())
                    .orElseThrow(() -> race);
        }
    }

    @Override
    public void onStart() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        Instant now = clock.instant();
        try {
            repository.findByExchangeAndStatusInOrderByNextAttemptAtAsc(exchange, INCOMPLETE_STATUSES)
                    .forEach(task -> schedule(task, scheduledAt(task, now)));
        } catch (RuntimeException exception) {
            started.set(false);
            cancelScheduledTasks();
            throw exception;
        }
    }

    private Instant scheduledAt(OrderReconciliationTask task, Instant now) {
        Instant next = task.getNextAttemptAt();
        return next == null || next.isBefore(now) ? now : next;
    }

    private void schedule(OrderReconciliationTask task, Instant startTime) {
        synchronized (schedulingMonitor) {
            if (!started.get() || runningTasks.contains(task.getId())) {
                return;
            }
            scheduledTasks.compute(task.getId(), (taskId, existing) -> {
                if (existing != null && !existing.isCancelled() && !existing.isDone()) {
                    return existing;
                }
                return taskScheduler.schedule(() -> reconcile(taskId), startTime);
            });
        }
    }

    private void reconcile(String taskId) {
        scheduledTasks.remove(taskId);
        if (!started.get() || !runningTasks.add(taskId)) {
            return;
        }

        OrderReconciliationTask retryTask = null;
        try {
            OrderReconciliationTask task = repository.findById(taskId).orElse(null);
            if (task == null
                    || task.getExchange() != exchange
                    || task.getStatus() == OrderReconciliationStatus.RESOLVED) {
                return;
            }

            Instant attemptAt = clock.instant();
            task.startAttempt(attemptAt);
            repository.save(task);

            OrderEvent.StatusReceived status;
            try {
                status = Objects.requireNonNull(
                    orderStatusReader.fetch(task.getTradingPair(), task.getClientOrderId()),
                    "주문 상태 조회 결과가 null입니다."
                );
            } catch (OrderNotFoundException notFound) {
                retryTask = recordRetry(task, notFound);
                return;
            } catch (RuntimeException failure) {
                publishStatusQueryFailure(task, failure);
                retryTask = recordRetry(task, failure);
                return;
            }

            eventPublisher.publish(status);
            task.resolve(status, clock.instant());
            repository.save(task);
            log.info(
                    "주문 reconciliation 완료: exchange={}, clientOrderId={}, attempts={}",
                    exchange,
                    task.getClientOrderId(),
                    task.getAttempts()
            );
        } catch (RuntimeException unexpected) {
            log.error("주문 reconciliation task 처리 실패: exchange={}, taskId={}", exchange, taskId, unexpected);
        } finally {
            runningTasks.remove(taskId);
            if (retryTask != null && started.get()) {
                schedule(retryTask, retryTask.getNextAttemptAt());
            }
        }
    }

    private OrderReconciliationTask recordRetry(OrderReconciliationTask task, RuntimeException failure) {
        Instant now = clock.instant();
        if (task.getAttempts() >= config.maxAttempts()) {
            task.markUnresolved(failure, now.plus(config.unresolvedRetryDelay()), now);
            log.warn(
                    "주문 상태 미확정, 느린 재조정으로 전환: exchange={}, clientOrderId={}, attempts={}",
                    exchange,
                    task.getClientOrderId(),
                    task.getAttempts()
            );
        } else {
            task.waitForRetry(failure, now.plus(backoff(task.getAttempts())), now);
        }
        return repository.save(task);
    }

    private Duration backoff(int attempts) {
        long multiplier = 1L << Math.min(Math.max(0, attempts - 1), 30);
        Duration calculated;
        try {
            calculated = config.initialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            return config.maxBackoff();
        }
        return calculated.compareTo(config.maxBackoff()) > 0 ? config.maxBackoff() : calculated;
    }

    private void publishStatusQueryFailure(OrderReconciliationTask task, RuntimeException cause) {
        eventPublisher.publish(new ExchangeFailureEvent(
                exchange,
                ExchangeOperation.ORDER_STATUS_QUERY,
                task.getTradingPair(),
                task.getClientOrderId(),
                task.getExchangeOrderId(),
                task.getStrategyId(),
                task.getExecutionGroupId(),
                cause,
                clock.instant()
        ));
    }

    @Override
    public void onShutdown() {
        synchronized (schedulingMonitor) {
            if (!started.compareAndSet(true, false)) {
                return;
            }
            cancelScheduledTasks();
        }
    }

    private void cancelScheduledTasks() {
        synchronized (schedulingMonitor) {
            scheduledTasks.values().forEach(task -> task.cancel(false));
            scheduledTasks.clear();
        }
    }

    @Override
    public int phase() {
        return Phases.ORDER_RECONCILIATION;
    }
}
