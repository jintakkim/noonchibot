package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeOperationSucceededEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.CONFIRMED_REJECTION;

/**
 * 거래소 요청 결과 이벤트를 정책, reconciliation, 거래소 허용 상태에 연결한다.
 */
@Slf4j
public class ExchangeFailureCoordinator implements LifecycleAware {
    private final EventSubscriber eventSubscriber;
    private final ExchangeFailurePolicy failurePolicy;
    private final ExchangeEligibilityRegistry eligibilityRegistry;
    private final OrderReconciliationSink reconciliationSink;
    private final EventPublisher eventPublisher;
    private final Set<Subscription> subscriptions = new HashSet<>();

    public ExchangeFailureCoordinator(
            EventSubscriber eventSubscriber,
            ExchangeFailurePolicy failurePolicy,
            ExchangeEligibilityRegistry eligibilityRegistry,
            OrderReconciliationSink reconciliationSink,
            EventPublisher eventPublisher
    ) {
        this.eventSubscriber = Objects.requireNonNull(eventSubscriber, "eventSubscriber");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.eligibilityRegistry = Objects.requireNonNull(eligibilityRegistry, "eligibilityRegistry");
        this.reconciliationSink = Objects.requireNonNull(reconciliationSink, "reconciliationSink");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    @Override
    public void onStart() {
        subscriptions.add(eventSubscriber.subscribe(
                ExchangeFailureEvent.class,
                this::handleFailure,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                ExchangeOperationSucceededEvent.class,
                this::handleSuccess,
                ExecutionPolicy.sequential()
        ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public int phase() {
        return Phases.EXCHANGE_FAILURE_COORDINATOR;
    }

    public void handleFailure(ExchangeFailureEvent event) {
        FailureDecision decision = failurePolicy.decide(event);
        switch (decision.action()) {
            case IGNORE -> handleIgnored(event, decision);
            case RECORD_FAILURE -> eligibilityRegistry.recordFailure(event.exchange(), event.cause());
            case RECONCILE_AND_QUARANTINE -> {
                eligibilityRegistry.quarantine(event.exchange(), event.cause());
                reconciliationSink.enqueue(event);
            }
            case MANUAL_BLOCK -> eligibilityRegistry.manualBlock(event.exchange(), event.cause());
        }
    }

    public void handleSuccess(ExchangeOperationSucceededEvent event) {
        eligibilityRegistry.recordOperationSuccess(event.exchange());
    }

    private void handleIgnored(ExchangeFailureEvent event, FailureDecision decision) {
        if (decision.kind() != CONFIRMED_REJECTION
                || event.operation() != ExchangeOperation.ORDER_PLACE) {
            return;
        }

        eventPublisher.publish(new OrderEvent.StatusReceived(
                event.tradingPair(),
                event.clientOrderId(),
                event.exchangeOrderId(),
                OrderState.REJECTED,
                event.occurredAt()
        ));
        log.info(
                "confirmed order rejection applied: exchange={}, pair={}, clientOrderId={}",
                event.exchange(),
                event.tradingPair(),
                event.clientOrderId()
        );
    }
}
