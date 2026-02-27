package com.hotak.noonchibot.connector.throttle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AsyncThrottlerTest {
    private AsyncThrottlerImpl throttler;
    private final TaskExecutor executor = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());

    @Nested
    @DisplayName("기본 실행")
    class BasicExecution {
        @BeforeEach
        void setUp() {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("REQUEST_WEIGHT", 6000, Duration.ofMinutes(1)),
                    RateLimit.pool("RAW_REQUESTS", 61000, Duration.ofMinutes(5)),
                    RateLimit.endpoint("/api/time", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)))
            ), executor);
        }

        @Test
        @DisplayName("등록된 limitId로 태스크를 실행하면 결과를 반환한다")
        void executesTaskAndReturnsResult() throws Exception {
            var result = throttler.execute("/api/time", () -> "ok").get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("알 수 없는 limitId는 rate limit 없이 태스크를 실행한다")
        void unknownLimitIdExecutesWithoutRateLimiting() throws Exception {
            var result = throttler.execute("unknown", () -> "ok").get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("여러 태스크를 순차 실행하면 모두 성공한다")
        void multipleTasksAllSucceed() throws Exception {
            var futures = IntStream.range(0, 10)
                    .mapToObj(i -> throttler.execute("/api/time", () -> i))
                    .toList();
            for (int i = 0; i < futures.size(); i++) {
                assertThat(futures.get(i).get(1, TimeUnit.SECONDS)).isEqualTo(i);
            }
        }
    }

    @Nested
    @DisplayName("Rate Limit 용량 제한")
    class CapacityLimiting {

        @Test
        @DisplayName("풀 한도를 초과하면 대기한다")
        void blocksWhenPoolExhausted() throws Exception {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("SMALL_POOL", 3, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/test", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("SMALL_POOL", 1)))
            ), executor, Duration.ofMillis(50), 0.0);

            // 3개 즉시 실행 (한도 소진)
            for (int i = 0; i < 3; i++) {
                throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);
            }

            // 4번째는 대기해야 함
            var start = System.currentTimeMillis();
            throttler.execute("/api/test", () -> "ok").get(5, TimeUnit.SECONDS);
            var elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThan(500);
        }

        @Test
        @DisplayName("시간 윈도우가 지나면 용량이 회복된다")
        void capacityRecoversAfterWindow() throws Exception {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("TINY_POOL", 2, Duration.ofSeconds(1)),
                    RateLimit.endpoint("/api/test", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("TINY_POOL", 1)))
            ), executor, Duration.ofMillis(50), 0.0);

            // 한도 소진
            throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);

            // 윈도우 경과 대기
            Thread.sleep(1100);

            // 다시 즉시 실행 가능
            var start = System.currentTimeMillis();
            throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);
            var elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(500);
        }
    }

    @Nested
    @DisplayName("Weight 처리")
    class WeightHandling {

        @Test
        @DisplayName("높은 weight의 요청은 풀 용량을 더 많이 소비한다")
        void highWeightConsumesMoreCapacity() throws Exception {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("POOL", 10, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/heavy", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("POOL", 5)))
            ), executor, Duration.ofMillis(50), 0.0);

            // weight 5 × 2번 = 10 (한도 소진)
            throttler.execute("/api/heavy", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/heavy", () -> "ok").get(1, TimeUnit.SECONDS);

            // 3번째는 대기
            var start = System.currentTimeMillis();
            throttler.execute("/api/heavy", () -> "ok").get(5, TimeUnit.SECONDS);
            var elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThan(500);
        }

        @Test
        @DisplayName("weightOverrides로 동적 weight를 적용할 수 있다")
        void weightOverrideIsApplied() throws Exception {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("POOL", 10, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/ticker", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("POOL", 2)))
            ), executor, Duration.ofMillis(50), 0.0);

            // weight를 10으로 override → 한 번에 한도 소진
            throttler.execute("/api/ticker", () -> "ok", Map.of("POOL", 10))
                    .get(1, TimeUnit.SECONDS);

            // 다음은 대기
            var start = System.currentTimeMillis();
            throttler.execute("/api/ticker", () -> "ok").get(5, TimeUnit.SECONDS);
            var elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThan(500);
        }
    }

    @Nested
    @DisplayName("여러 풀에 동시 소비")
    class MultiplePoolConsumption {
        @Test
        @DisplayName("linkedLimits로 연결된 모든 풀에서 용량을 소비한다")
        void consumesFromAllLinkedPools() throws Exception {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("WEIGHT", 100, Duration.ofSeconds(2)),
                    RateLimit.pool("ORDERS", 2, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/order", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("WEIGHT", 4),
                            new RateLimit.LinkedLimitWeightPair("ORDERS", 1)))
            ), executor, Duration.ofMillis(50), 0.0);

            // ORDERS 풀: 한도 2, weight 1 × 2번 = 소진
            throttler.execute("/api/order", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/order", () -> "ok").get(1, TimeUnit.SECONDS);

            // WEIGHT는 아직 여유(8/100)지만 ORDERS가 꽉 참 → 대기
            var start = System.currentTimeMillis();
            throttler.execute("/api/order", () -> "ok").get(5, TimeUnit.SECONDS);
            var elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThan(500);
        }
    }

    @Nested
    @DisplayName("자기 자신 풀 소비")
    class SelfPoolConsumption {

        @Test
        @DisplayName("linkedLimits 없는 endpoint는 자기 자신의 풀에서 소비한다")
        void endpointWithoutLinkedLimitsUsesSelfPool() throws Exception {
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("/api/simple", 2, Duration.ofSeconds(2))
            ), executor, Duration.ofMillis(50), 0.0);

            throttler.execute("/api/simple", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/simple", () -> "ok").get(1, TimeUnit.SECONDS);

            var start = System.currentTimeMillis();
            throttler.execute("/api/simple", () -> "ok").get(5, TimeUnit.SECONDS);
            var elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isGreaterThan(500);
        }
    }
}
