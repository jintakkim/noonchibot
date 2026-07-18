package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.Exchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.ELIGIBLE;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.MANUAL_BLOCKED;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.PROBING;
import static com.hotak.noonchibot.core.exchange.ExchangeEligibilityStatus.QUARANTINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeEligibilityRegistryTest {
    private static final Exchange EXCHANGE = Exchange.BINANCE_DERIVATIVE;
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Test
    @DisplayName("모든 거래소는 초기 상태에서 신규 주문이 허용된다")
    void initialState_isEligibleForEveryExchange() {
        ExchangeEligibilityRegistry registry = registry(3);

        assertThat(registry.snapshot()).hasSize(Exchange.values().length);
        assertThat(registry.snapshot().values())
                .allSatisfy(eligibility -> {
                    assertThat(eligibility.status()).isEqualTo(ELIGIBLE);
                    assertThat(eligibility.consecutiveFailures()).isZero();
                });
    }

    @Test
    @DisplayName("연속 실패가 임계치에 도달하면 거래소를 격리한다")
    void consecutiveFailuresAtThreshold_quarantinesExchange() {
        ExchangeEligibilityRegistry registry = registry(3);

        registry.recordFailure(EXCHANGE, new IllegalStateException("first"));
        registry.recordFailure(EXCHANGE, new IllegalStateException("second"));

        assertThat(registry.status(EXCHANGE)).isEqualTo(ELIGIBLE);

        Throwable lastFailure = new IllegalStateException("third");
        registry.recordFailure(EXCHANGE, lastFailure);

        assertThat(registry.status(EXCHANGE)).isEqualTo(QUARANTINED);
        assertThat(registry.isEligible(EXCHANGE)).isFalse();
        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures()).isEqualTo(3);
        assertThat(registry.eligibility(EXCHANGE).lastFailure()).isSameAs(lastFailure);
    }

    @Test
    @DisplayName("정상 요청이 들어오면 임계치 전 연속 실패 횟수를 초기화한다")
    void operationSuccess_resetsConsecutiveFailures() {
        ExchangeEligibilityRegistry registry = registry(3);
        registry.recordFailure(EXCHANGE, new IllegalStateException("first"));
        registry.recordFailure(EXCHANGE, new IllegalStateException("second"));

        registry.recordOperationSuccess(EXCHANGE);
        registry.recordFailure(EXCHANGE, new IllegalStateException("after success"));

        assertThat(registry.status(EXCHANGE)).isEqualTo(ELIGIBLE);
        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures()).isOne();
    }

    @Test
    @DisplayName("불명확한 주문 결과는 실패 임계치 전에도 거래소를 즉시 격리한다")
    void ambiguousOrderResult_quarantinesImmediately() {
        ExchangeEligibilityRegistry registry = registry(3);

        registry.quarantine(EXCHANGE, new IllegalStateException("unknown order state"));

        assertThat(registry.status(EXCHANGE)).isEqualTo(QUARANTINED);
        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures()).isOne();
    }

    @Test
    @DisplayName("격리된 거래소는 탐색 성공 시에만 다시 허용 상태가 된다")
    void successfulProbe_restoresEligibility() {
        ExchangeEligibilityRegistry registry = registry(3);
        registry.quarantine(EXCHANGE, new IllegalStateException("timeout"));

        registry.beginProbe(EXCHANGE);

        assertThat(registry.status(EXCHANGE)).isEqualTo(PROBING);
        assertThat(registry.isEligible(EXCHANGE)).isFalse();

        registry.recordProbeSuccess(EXCHANGE);

        assertThat(registry.status(EXCHANGE)).isEqualTo(ELIGIBLE);
        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures()).isZero();
        assertThat(registry.eligibility(EXCHANGE).lastFailure()).isNull();
    }

    @Test
    @DisplayName("탐색 실패 시 거래소를 다시 격리하고 실패 원인을 보존한다")
    void failedProbe_returnsToQuarantine() {
        ExchangeEligibilityRegistry registry = registry(3);
        registry.quarantine(EXCHANGE, new IllegalStateException("timeout"));
        registry.beginProbe(EXCHANGE);
        Throwable probeFailure = new IllegalStateException("probe failed");

        registry.recordProbeFailure(EXCHANGE, probeFailure);

        assertThat(registry.status(EXCHANGE)).isEqualTo(QUARANTINED);
        assertThat(registry.eligibility(EXCHANGE).lastFailure()).isSameAs(probeFailure);
    }

    @Test
    @DisplayName("수동 차단 상태는 일반 성공이나 자동 복구로 해제되지 않는다")
    void manualBlock_isNotAutomaticallyRestored() {
        ExchangeEligibilityRegistry registry = registry(3);
        registry.manualBlock(EXCHANGE, new IllegalStateException("invalid credentials"));

        registry.recordOperationSuccess(EXCHANGE);
        registry.beginProbe(EXCHANGE);
        registry.markEligible(EXCHANGE);

        assertThat(registry.status(EXCHANGE)).isEqualTo(MANUAL_BLOCKED);
    }

    @Test
    @DisplayName("동시 실패 기록도 누락 없이 원자적으로 누적한다")
    void concurrentFailures_areRecordedAtomically() throws Exception {
        ExchangeEligibilityRegistry registry = registry(100);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 100)
                    .mapToObj(index -> executor.submit(() ->
                            registry.recordFailure(EXCHANGE, new IllegalStateException("failure-" + index))))
                    .toList();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(registry.eligibility(EXCHANGE).consecutiveFailures()).isEqualTo(100);
        assertThat(registry.status(EXCHANGE)).isEqualTo(QUARANTINED);
    }

    @Test
    @DisplayName("실패 임계치는 1 이상이어야 한다")
    void nonPositiveThreshold_isRejected() {
        assertThatThrownBy(() -> registry(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureThreshold must be positive");
    }

    private ExchangeEligibilityRegistry registry(int threshold) {
        return new ExchangeEligibilityRegistry(
                threshold,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
