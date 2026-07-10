package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.connector.ExchangeProtocolException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import com.hotak.noonchibot.core.order.OrderValidationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceConfigTest {
    private static final long FAST_CALL_MILLIS = 1_999;
    private static final long SLOW_CALL_MILLIS = 2_001;

    private final ResilienceConfig resilienceConfig = new ResilienceConfig();

    @Test
    @DisplayName("기본 서킷브레이커는 운영 임계값과 time-based window 설정을 사용한다")
    void defaultCircuitBreaker_usesProductionThresholdsAndTimeBasedWindow() {
        CircuitBreakerConfig config = circuitBreaker("settings").getCircuitBreakerConfig();

        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
        assertThat(config.getSlidingWindowType())
                .isEqualTo(CircuitBreakerConfig.SlidingWindowType.TIME_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }

    @Test
    @DisplayName("최소 호출 수 10회 전에는 실패가 누적되어도 OPEN되지 않는다")
    void failures_doNotOpenCircuitBeforeMinimumNumberOfCalls() {
        CircuitBreaker circuitBreaker = circuitBreaker("minimum-calls");

        recordFailures(circuitBreaker, 9);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(9);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(9);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0f);

        recordFailures(circuitBreaker, 1);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0f);
    }

    @Test
    @DisplayName("실패율 50%에서 OPEN되고 40%에서는 CLOSED를 유지한다")
    void failureRate_opensAtFiftyPercentButNotBelowIt() {
        CircuitBreaker belowThreshold = circuitBreaker("failure-rate-below");
        recordFailures(belowThreshold, 4);
        recordFastSuccesses(belowThreshold, 6);

        assertThat(belowThreshold.getMetrics().getFailureRate()).isEqualTo(40.0f);
        assertThat(belowThreshold.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        CircuitBreaker atThreshold = circuitBreaker("failure-rate-at-threshold");
        recordFailures(atThreshold, 5);
        recordFastSuccesses(atThreshold, 5);

        assertThat(atThreshold.getMetrics().getFailureRate()).isEqualTo(50.0f);
        assertThat(atThreshold.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("slow call 비율 50%에서 OPEN되고 40%에서는 CLOSED를 유지한다")
    void slowCallRate_opensAtFiftyPercentWithoutWaitingForRealCalls() {
        CircuitBreaker belowThreshold = circuitBreaker("slow-rate-below");
        recordSlowSuccesses(belowThreshold, 4);
        recordFastSuccesses(belowThreshold, 6);

        assertThat(belowThreshold.getMetrics().getSlowCallRate()).isEqualTo(40.0f);
        assertThat(belowThreshold.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(4);
        assertThat(belowThreshold.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        CircuitBreaker atThreshold = circuitBreaker("slow-rate-at-threshold");
        recordSlowSuccesses(atThreshold, 5);
        recordFastSuccesses(atThreshold, 5);

        assertThat(atThreshold.getMetrics().getFailureRate()).isZero();
        assertThat(atThreshold.getMetrics().getSlowCallRate()).isEqualTo(50.0f);
        assertThat(atThreshold.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("OPEN 대기시간 후 HALF_OPEN 호출 3개만 허용하고 모두 성공하면 CLOSED로 복귀한다")
    void openCircuit_afterWaitAllowsOnlyThreeHalfOpenCallsAndClosesOnSuccess() {
        AdjustableClock clock = new AdjustableClock();
        CircuitBreaker circuitBreaker = circuitBreaker("half-open-success", clock);
        circuitBreaker.transitionToOpenState();

        clock.advance(Duration.ofSeconds(30).plusMillis(1));

        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();

        recordFastSuccesses(circuitBreaker, 3);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN 시험 호출 실패율이 임계값에 도달하면 다시 OPEN된다")
    void halfOpenCircuit_reopensWhenTrialFailureRateReachesThreshold() {
        AdjustableClock clock = new AdjustableClock();
        CircuitBreaker circuitBreaker = circuitBreaker("half-open-failure", clock);
        circuitBreaker.transitionToOpenState();
        clock.advance(Duration.ofSeconds(30).plusMillis(1));

        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();

        recordFailures(circuitBreaker, 2);
        recordFastSuccesses(circuitBreaker, 1);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("인프라 예외는 서킷 실패로 기록한다")
    void configuredInfrastructureExceptions_areRecordedAsFailures() {
        List<Exception> recordedExceptions = List.of(
                new ExchangeTransientException(HttpStatusCode.valueOf(503), "transient"),
                new ExchangeProtocolException("protocol", null),
                new RestClientException("rest client"),
                new IOException("io"),
                new TimeoutException("timeout"),
                new IllegalStateException("illegal state"),
                new NullPointerException("null"),
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker("blocked-source"))
        );

        for (Exception exception : recordedExceptions) {
            CircuitBreaker circuitBreaker = circuitBreaker("recorded-" + exception.getClass().getSimpleName());

            assertThatThrownBy(() -> circuitBreaker.executeCallable(() -> {
                throw exception;
            })).isSameAs(exception);

            assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                    .as(exception.getClass().getSimpleName())
                    .isEqualTo(1);
            assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        }
    }

    @Test
    @DisplayName("거래소 거절과 주문 검증 예외는 서킷 통계에서 제외한다")
    void businessRejectionsAndValidationErrors_areIgnored() {
        List<RuntimeException> ignoredExceptions = List.of(
                new ExchangeRejectedException("rejected"),
                new OrderValidationException("client-order-id", "BTCUSDT", "invalid"),
                new OrderValidationException.UnsupportedOrderTypeException(
                        "client-order-id",
                        "BTCUSDT",
                        "unsupported order type"
                )
        );

        for (RuntimeException exception : ignoredExceptions) {
            CircuitBreaker circuitBreaker = circuitBreaker("ignored-" + exception.getClass().getSimpleName());

            assertThatThrownBy(() -> circuitBreaker.executeCallable(() -> {
                throw exception;
            })).isSameAs(exception);

            assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls())
                    .as(exception.getClass().getSimpleName())
                    .isZero();
            assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
            assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        }
    }

    @Test
    @DisplayName("분류 대상이 아닌 예외는 서킷 실패로 기록하지 않는다")
    void exceptionOutsideConfiguredCategories_isNotRecordedAsFailure() {
        CircuitBreaker circuitBreaker = circuitBreaker("unrecorded");
        IllegalArgumentException exception = new IllegalArgumentException("programming error");

        assertThatThrownBy(() -> circuitBreaker.executeCallable(() -> {
            throw exception;
        })).isSameAs(exception);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    private CircuitBreaker circuitBreaker(String name) {
        return resilienceConfig.circuitBreakerRegistry().circuitBreaker(name);
    }

    private CircuitBreaker circuitBreaker(String name, Clock clock) {
        CircuitBreakerConfig productionConfig = circuitBreaker(name + "-production-config")
                .getCircuitBreakerConfig();
        CircuitBreakerConfig testConfig = CircuitBreakerConfig.from(productionConfig)
                .clock(clock)
                .build();
        return CircuitBreaker.of(name, testConfig);
    }

    private static final class AdjustableClock extends Clock {
        private Instant current = Instant.EPOCH;
        private final ZoneId zone;

        private AdjustableClock() {
            this(ZoneId.of("UTC"));
        }

        private AdjustableClock(ZoneId zone) {
            this.zone = zone;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            AdjustableClock clock = new AdjustableClock(zone);
            clock.current = current;
            return clock;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static void recordFailures(CircuitBreaker circuitBreaker, int count) {
        for (int i = 0; i < count; i++) {
            circuitBreaker.onError(
                    FAST_CALL_MILLIS,
                    TimeUnit.MILLISECONDS,
                    new ExchangeTransientException(HttpStatusCode.valueOf(503), "transient")
            );
        }
    }

    private static void recordFastSuccesses(CircuitBreaker circuitBreaker, int count) {
        for (int i = 0; i < count; i++) {
            circuitBreaker.onSuccess(FAST_CALL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static void recordSlowSuccesses(CircuitBreaker circuitBreaker, int count) {
        for (int i = 0; i < count; i++) {
            circuitBreaker.onSuccess(SLOW_CALL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }
}
