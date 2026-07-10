package com.hotak.noonchibot.core.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ExchangeHealthRegistryTest {
    @Test
    @DisplayName("등록되지 않은 서킷은 사용 가능한 상태로 취급한다")
    void unknownCircuit_isAvailableAndHasNoHealthEntry() {
        ExchangeHealthRegistry registry = new ExchangeHealthRegistry();

        assertThat(registry.isAvailable("BINANCE_SPOT.order-entry")).isTrue();
        assertThat(registry.find("BINANCE_SPOT.order-entry")).isEmpty();
    }

    @ParameterizedTest(name = "{0} 상태의 사용 가능 여부는 {1}이다")
    @DisplayName("서킷 상태에 따라 사용 가능 여부를 반환한다")
    @MethodSource("availabilityByState")
    void availability_reflectsCircuitState(CircuitBreaker.State state, boolean expectedAvailable) {
        ExchangeHealthRegistry registry = new ExchangeHealthRegistry();
        String circuitName = "BINANCE_SPOT.order-entry";

        registry.updateState(circuitName, state);

        assertThat(registry.isAvailable(circuitName)).isEqualTo(expectedAvailable);
        assertThat(registry.find(circuitName))
                .hasValueSatisfying(health -> {
                    assertThat(health.circuitName()).isEqualTo(circuitName);
                    assertThat(health.state()).isEqualTo(state);
                    assertThat(health.callNotPermittedCount()).isZero();
                    assertThat(health.updatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("상태가 변경되어도 차단 호출 누적 횟수를 유지한다")
    void stateUpdate_preservesCallNotPermittedCount() {
        ExchangeHealthRegistry registry = new ExchangeHealthRegistry();
        String circuitName = "BINANCE_SPOT.order-entry";
        registry.updateState(circuitName, CircuitBreaker.State.OPEN);
        registry.markCallNotPermitted(circuitName);
        registry.markCallNotPermitted(circuitName);
        Instant beforeStateUpdate = registry.find(circuitName).orElseThrow().updatedAt();

        registry.updateState(circuitName, CircuitBreaker.State.HALF_OPEN);

        assertThat(registry.find(circuitName))
                .hasValueSatisfying(health -> {
                    assertThat(health.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
                    assertThat(health.callNotPermittedCount()).isEqualTo(2);
                    assertThat(health.updatedAt()).isAfterOrEqualTo(beforeStateUpdate);
                });
    }

    @Test
    @DisplayName("등록되지 않은 서킷에서 첫 호출이 차단되면 OPEN 상태로 초기화한다")
    void firstCallNotPermitted_initializesUnknownCircuitAsOpen() {
        ExchangeHealthRegistry registry = new ExchangeHealthRegistry();
        String circuitName = "BINANCE_SPOT.order-entry";

        registry.markCallNotPermitted(circuitName);

        assertThat(registry.find(circuitName))
                .hasValueSatisfying(health -> {
                    assertThat(health.state()).isEqualTo(CircuitBreaker.State.OPEN);
                    assertThat(health.callNotPermittedCount()).isOne();
                });
        assertThat(registry.isAvailable(circuitName)).isFalse();
    }

    @Test
    @DisplayName("동시에 차단 횟수를 갱신해도 집계가 유실되지 않는다")
    void concurrentCallNotPermittedUpdates_doNotLoseCounts() throws Exception {
        ExchangeHealthRegistry registry = new ExchangeHealthRegistry();
        String circuitName = "BINANCE_SPOT.order-entry";
        int workerCount = 8;
        int updatesPerWorker = 250;
        CountDownLatch workersReady = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(workerCount)) {
            List<? extends Future<?>> futures = IntStream.range(0, workerCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        workersReady.countDown();
                        start.await();
                        for (int i = 0; i < updatesPerWorker; i++) {
                            registry.markCallNotPermitted(circuitName);
                        }
                        return null;
                    }))
                    .toList();

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertThat(registry.find(circuitName))
                .hasValueSatisfying(health ->
                        assertThat(health.callNotPermittedCount())
                                .isEqualTo((long) workerCount * updatesPerWorker));
    }

    private static Stream<Arguments> availabilityByState() {
        return Arrays.stream(CircuitBreaker.State.values())
                .map(state -> arguments(
                        state,
                        state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN
                ));
    }
}
