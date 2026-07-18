package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.Exchange;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.ELIGIBLE;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.MANUAL_BLOCKED;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.PROBING;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.QUARANTINED;

/**
 * 거래소별 신규 주문 허용 상태를 원자적으로 관리한다.
 */
public class ExchangeEligibilityRegistry implements ExchangeEligibilityView {
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;

    private final ConcurrentMap<Exchange, ExchangeEligibility> entries = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final Clock clock;

    public ExchangeEligibilityRegistry() {
        this(DEFAULT_FAILURE_THRESHOLD);
    }

    public ExchangeEligibilityRegistry(int failureThreshold) {
        this(failureThreshold, Clock.systemUTC());
    }

    ExchangeEligibilityRegistry(int failureThreshold, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.clock = Objects.requireNonNull(clock, "clock");

        Instant initializedAt = clock.instant();
        Arrays.stream(Exchange.values()).forEach(exchange -> entries.put(
                exchange,
                new ExchangeEligibility(exchange, ELIGIBLE, 0, null, initializedAt)
        ));
    }

    @Override
    public ExchangeEligibility eligibility(Exchange exchange) {
        return entries.get(Objects.requireNonNull(exchange, "exchange"));
    }

    @Override
    public ExchangeEligibilityStatus status(Exchange exchange) {
        return eligibility(exchange).status();
    }

    @Override
    public boolean isEligible(Exchange exchange) {
        return status(exchange) == ELIGIBLE;
    }

    @Override
    public Map<Exchange, ExchangeEligibility> snapshot() {
        return Map.copyOf(entries);
    }

    public ExchangeEligibility recordFailure(Exchange exchange, Throwable cause) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(cause, "cause");
        return entries.compute(exchange, (ignored, current) -> {
            if (current.status() == MANUAL_BLOCKED) {
                return current;
            }
            int failures = current.consecutiveFailures() + 1;
            ExchangeEligibilityStatus nextStatus = failures >= failureThreshold
                    ? QUARANTINED
                    : current.status();
            return changed(current, nextStatus, failures, cause);
        });
    }

    /**
     * 결과가 불명확한 주문이 존재할 때 임계치와 관계없이 신규 주문을 즉시 격리한다.
     */
    public ExchangeEligibility quarantine(Exchange exchange, Throwable cause) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(cause, "cause");
        return entries.compute(exchange, (ignored, current) -> {
            if (current.status() == MANUAL_BLOCKED) {
                return current;
            }
            return changed(current, QUARANTINED, current.consecutiveFailures() + 1, cause);
        });
    }

    public ExchangeEligibility manualBlock(Exchange exchange, Throwable cause) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(cause, "cause");
        return entries.compute(exchange, (ignored, current) ->
                changed(current, MANUAL_BLOCKED, current.consecutiveFailures() + 1, cause)
        );
    }

    public ExchangeEligibility beginProbe(Exchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return entries.compute(exchange, (ignored, current) -> current.status() == QUARANTINED
                ? changed(current, PROBING, current.consecutiveFailures(), current.lastFailure())
                : current
        );
    }

    public ExchangeEligibility recordProbeSuccess(Exchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return entries.compute(exchange, (ignored, current) -> current.status() == PROBING
                ? changed(current, ELIGIBLE, 0, null)
                : current
        );
    }

    public ExchangeEligibility recordProbeFailure(Exchange exchange, Throwable cause) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(cause, "cause");
        return entries.compute(exchange, (ignored, current) -> current.status() == PROBING
                ? changed(current, QUARANTINED, current.consecutiveFailures() + 1, cause)
                : current
        );
    }

    /**
     * 일반 요청 성공은 연속 실패 횟수만 초기화하며 격리 상태를 자동 해제하지 않는다.
     */
    public ExchangeEligibility recordOperationSuccess(Exchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return entries.compute(exchange, (ignored, current) -> {
            if (current.status() == MANUAL_BLOCKED || current.consecutiveFailures() == 0) {
                return current;
            }
            Throwable lastFailure = current.status() == ELIGIBLE ? null : current.lastFailure();
            return changed(current, current.status(), 0, lastFailure);
        });
    }

    /**
     * 명시적인 복구 경로에서 사용한다. 수동 차단은 이 메서드로 해제되지 않는다.
     */
    public ExchangeEligibility markEligible(Exchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return entries.compute(exchange, (ignored, current) -> current.status() == MANUAL_BLOCKED
                ? current
                : changed(current, ELIGIBLE, 0, null)
        );
    }

    private ExchangeEligibility changed(
            ExchangeEligibility current,
            ExchangeEligibilityStatus status,
            int consecutiveFailures,
            Throwable lastFailure
    ) {
        return new ExchangeEligibility(
                current.exchange(),
                status,
                consecutiveFailures,
                lastFailure,
                clock.instant()
        );
    }
}
