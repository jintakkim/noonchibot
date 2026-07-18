package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.connector.ExchangeAuthenticationException;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeOperationSucceededEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderReconciliationStatus;
import com.hotak.noonchibot.core.order.OrderReconciliationTask;
import com.hotak.noonchibot.core.order.OrderReconciliationTaskRepository;
import com.hotak.noonchibot.core.order.OrderStatusReader;
import com.hotak.noonchibot.core.order.OrderTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.ELIGIBLE;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.MANUAL_BLOCKED;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.PROBING;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.QUARANTINED;

/**
 * 격리된 거래소의 인증 주문상태 API를 읽어 신규 주문 가능 여부를 보수적으로 복구한다.
 */
@Slf4j
public class ExchangeRecoveryProbe implements LifecycleAware {
    private final Exchange exchange;
    private final ExchangeEligibilityRegistry eligibilityRegistry;
    private final OrderReconciliationTaskRepository reconciliationRepository;
    private final OrderStatusReader orderStatusReader;
    private final EventPublisher eventPublisher;
    private final TaskScheduler taskScheduler;
    private final OrderTracker orderTracker;
    private final ExchangeRecoveryProbeConfig config;
    private final Clock clock;
    private final AtomicBoolean started = new AtomicBoolean();

    private volatile ScheduledFuture<?> scheduledTask;
    private int consecutiveSuccesses;

    public ExchangeRecoveryProbe(
            Exchange exchange,
            ExchangeEligibilityRegistry eligibilityRegistry,
            OrderReconciliationTaskRepository reconciliationRepository,
            OrderStatusReader orderStatusReader,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            OrderTracker orderTracker
    ) {
        this(
                exchange,
                eligibilityRegistry,
                reconciliationRepository,
                orderStatusReader,
                eventPublisher,
                taskScheduler,
                orderTracker,
                ExchangeRecoveryProbeConfig.DEFAULT,
                Clock.systemUTC()
        );
    }

    public ExchangeRecoveryProbe(
            Exchange exchange,
            ExchangeEligibilityRegistry eligibilityRegistry,
            OrderReconciliationTaskRepository reconciliationRepository,
            OrderStatusReader orderStatusReader,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            OrderTracker orderTracker,
            ExchangeRecoveryProbeConfig config,
            Clock clock
    ) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.eligibilityRegistry = Objects.requireNonNull(eligibilityRegistry, "eligibilityRegistry");
        this.reconciliationRepository = Objects.requireNonNull(reconciliationRepository, "reconciliationRepository");
        this.orderStatusReader = Objects.requireNonNull(orderStatusReader, "orderStatusReader");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.orderTracker = Objects.requireNonNull(orderTracker, "orderTracker");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void onStart() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduledTask = taskScheduler.scheduleWithFixedDelay(this::probeIfNecessary, config.probeInterval());
        } catch (RuntimeException exception) {
            started.set(false);
            throw exception;
        }
    }

    void probeIfNecessary() {
        if (!started.get()) {
            return;
        }

        ExchangeEligibility eligibility = eligibilityRegistry.eligibility(exchange);
        if (eligibility.status() == MANUAL_BLOCKED || eligibility.status() == ELIGIBLE) {
            consecutiveSuccesses = 0;
            return;
        }
        if (eligibility.status() == QUARANTINED
                && clock.instant().isBefore(eligibility.updatedAt().plus(config.cooldown()))) {
            return;
        }
        if (eligibility.status() != QUARANTINED && eligibility.status() != PROBING) {
            return;
        }

        Optional<ProbeTarget> target;
        try {
            target = findTarget();
        } catch (RuntimeException repositoryFailure) {
            log.warn("복구 probe 대상 조회 실패: exchange={}", exchange, repositoryFailure);
            return;
        }
        if (target.isEmpty()) {
            log.debug("복구 probe를 위한 기존 주문이 없음: exchange={}", exchange);
            return;
        }

        if (eligibility.status() == QUARANTINED) {
            eligibilityRegistry.beginProbe(exchange);
        }
        executeProbe(target.get());
    }

    private Optional<ProbeTarget> findTarget() {
        Optional<OrderReconciliationTask> latestTask = reconciliationRepository
                .findFirstByExchangeOrderByUpdatedAtDesc(exchange);
        if (latestTask.isPresent()) {
            OrderReconciliationTask task = latestTask.get();
            return Optional.of(new ProbeTarget(
                    task.getTradingPair(),
                    task.getClientOrderId(),
                    task.getExchangeOrderId(),
                    task.getStrategyId(),
                    task.getExecutionGroupId(),
                    task
            ));
        }

        return orderTracker.getAllInFlightOrders().stream()
                .max(Comparator.comparing(
                        InFlightOrder::getCreationTimestamp,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(order -> new ProbeTarget(
                        order.getTradingPair(),
                        order.getClientOrderId(),
                        order.getExchangeOrderId(),
                        null,
                        null,
                        null
                ));
    }

    private void executeProbe(ProbeTarget target) {
        try {
            OrderEvent.StatusReceived status = Objects.requireNonNull(
                    orderStatusReader.fetch(target.tradingPair(), target.clientOrderId()),
                    "복구 probe 주문 상태 결과가 null입니다."
            );
            eventPublisher.publish(status);
            resolveTask(target.task(), status);
            recordHealthyProbe();
        } catch (ExchangeAuthenticationException authenticationFailure) {
            recordFailedProbe(target, authenticationFailure);
        } catch (ExchangeRejectedException definitiveResponse) {
            // 주문 유무/거절 결과와 별개로 인증된 상태조회 엔드포인트가 응답했다.
            recordHealthyProbe();
        } catch (RuntimeException transportFailure) {
            recordFailedProbe(target, transportFailure);
        }
    }

    private void resolveTask(OrderReconciliationTask task, OrderEvent.StatusReceived status) {
        if (task == null || task.getStatus() == OrderReconciliationStatus.RESOLVED) {
            return;
        }
        try {
            task.resolve(status, clock.instant());
            reconciliationRepository.save(task);
        } catch (RuntimeException persistenceFailure) {
            // 상태조회 transport 성공 여부와 로컬 저장 실패를 섞지 않는다.
            log.warn(
                    "복구 probe 결과의 reconciliation task 저장 실패: exchange={}, clientOrderId={}",
                    exchange,
                    task.getClientOrderId(),
                    persistenceFailure
            );
        }
    }

    private void recordHealthyProbe() {
        eventPublisher.publish(new ExchangeOperationSucceededEvent(
                exchange,
                ExchangeOperation.ORDER_STATUS_QUERY,
                clock.instant()
        ));
        consecutiveSuccesses++;
        if (consecutiveSuccesses >= config.requiredConsecutiveSuccesses()) {
            eligibilityRegistry.recordProbeSuccess(exchange);
            consecutiveSuccesses = 0;
            log.info("거래소 신규 주문 자격 복구: exchange={}", exchange);
        }
    }

    private void recordFailedProbe(ProbeTarget target, RuntimeException cause) {
        consecutiveSuccesses = 0;
        eligibilityRegistry.recordProbeFailure(exchange, cause);
        eventPublisher.publish(new ExchangeFailureEvent(
                exchange,
                ExchangeOperation.ORDER_STATUS_QUERY,
                target.tradingPair(),
                target.clientOrderId(),
                target.exchangeOrderId(),
                target.strategyId(),
                target.executionGroupId(),
                cause,
                clock.instant()
        ));
    }

    @Override
    public void onShutdown() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> task = scheduledTask;
        scheduledTask = null;
        if (task != null) {
            task.cancel(false);
        }
        consecutiveSuccesses = 0;
    }

    @Override
    public int phase() {
        return Phases.EXCHANGE_RECOVERY_PROBE;
    }

    private record ProbeTarget(
            String tradingPair,
            String clientOrderId,
            String exchangeOrderId,
            String strategyId,
            String executionGroupId,
            OrderReconciliationTask task
    ) {
    }
}
