package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "order_reconciliation_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_reconciliation_exchange_client_id",
                columnNames = {"exchange", "client_order_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReconciliationTask {
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Exchange exchange;

    @Column(name = "trading_pair", nullable = false, updatable = false)
    private String tradingPair;

    @Column(name = "client_order_id", nullable = false, updatable = false)
    private String clientOrderId;

    @Column(name = "exchange_order_id")
    private String exchangeOrderId;

    @Column(name = "strategy_id", updatable = false)
    private String strategyId;

    @Column(name = "execution_group_id", updatable = false)
    private String executionGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderReconciliationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static OrderReconciliationTask from(ExchangeFailureEvent event, Instant now) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(now, "now");
        if (event.clientOrderId() == null || event.clientOrderId().isBlank()) {
            throw new IllegalArgumentException("clientOrderId가 없는 실패는 주문 상태를 조정할 수 없습니다.");
        }
        if (event.tradingPair() == null || event.tradingPair().isBlank()) {
            throw new IllegalArgumentException("tradingPair이 없는 실패는 주문 상태를 조정할 수 없습니다.");
        }

        OrderReconciliationTask task = new OrderReconciliationTask();
        task.id = UUID.randomUUID().toString();
        task.exchange = Objects.requireNonNull(event.exchange(), "exchange");
        task.tradingPair = event.tradingPair();
        task.clientOrderId = event.clientOrderId();
        task.exchangeOrderId = event.exchangeOrderId();
        task.strategyId = event.strategyId();
        task.executionGroupId = event.executionGroupId();
        task.status = OrderReconciliationStatus.PENDING;
        task.createdAt = now;
        task.updatedAt = now;
        task.nextAttemptAt = now;
        task.lastError = errorMessage(event.cause());
        return task;
    }

    public void startAttempt(Instant now) {
        if (status == OrderReconciliationStatus.RESOLVED) {
            throw new IllegalStateException("이미 해결된 reconciliation task입니다.");
        }
        attempts++;
        lastAttemptAt = now;
        updatedAt = now;
        nextAttemptAt = null;
    }

    public void resolve(OrderEvent.StatusReceived result, Instant now) {
        if (result.exchangeOrderId() != null) {
            exchangeOrderId = result.exchangeOrderId();
        }
        status = OrderReconciliationStatus.RESOLVED;
        lastError = null;
        nextAttemptAt = null;
        resolvedAt = now;
        updatedAt = now;
    }

    public void waitForRetry(Throwable cause, Instant nextAttemptAt, Instant now) {
        status = OrderReconciliationStatus.WAITING_RETRY;
        lastError = errorMessage(cause);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        updatedAt = now;
    }

    public void markUnresolved(Throwable cause, Instant nextAttemptAt, Instant now) {
        status = OrderReconciliationStatus.UNRESOLVED;
        lastError = errorMessage(cause);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        updatedAt = now;
    }

    private static String errorMessage(Throwable cause) {
        if (cause == null) {
            return null;
        }
        String message = cause.getMessage();
        String value = cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
