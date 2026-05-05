package com.hotak.noonchibot.connector.throttle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AsyncPassthroughThrottlerTest {
    private AsyncThrottlerImpl throttler;
    private TestClock clock;
    private final TaskExecutor executor = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());

    @Nested
    @DisplayName("기본 실행")
    class BasicExecution {
        @BeforeEach
        void setUp() {
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("REQUEST_WEIGHT", 6000, Duration.ofMinutes(1)),
                    RateLimit.pool("RAW_REQUESTS", 61000, Duration.ofMinutes(5)),
                    RateLimit.endpoint("/api/time", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)))
            ), executor, Duration.ofMillis(10), 0.0, clock);
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
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("SMALL_POOL", 3, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/test", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("SMALL_POOL", 1)))
            ), executor, Duration.ofMillis(10), 0.0, clock);

            for (int i = 0; i < 3; i++) {
                throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);
            }

            // 4번째는 용량 부족으로 대기 중
            var future = throttler.execute("/api/test", () -> "ok");
            Thread.sleep(50); // 루프 진입 대기
            assertThat(future.isDone()).isFalse();

            // 시간 전진 → 용량 회복 → 즉시 완료
            clock.advance(Duration.ofSeconds(3));
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        }

        @Test
        @DisplayName("시간 윈도우가 지나면 용량이 회복된다")
        void capacityRecoversAfterWindow() throws Exception {
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("TINY_POOL", 2, Duration.ofSeconds(1)),
                    RateLimit.endpoint("/api/test", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("TINY_POOL", 1)))
            ), executor, Duration.ofMillis(10), 0.0, clock);

            throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);

            clock.advance(Duration.ofSeconds(2));

            var result = throttler.execute("/api/test", () -> "ok").get(1, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("Weight 처리")
    class WeightHandling {

        @Test
        @DisplayName("높은 weight의 요청은 풀 용량을 더 많이 소비한다")
        void highWeightConsumesMoreCapacity() throws Exception {
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("POOL", 10, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/heavy", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("POOL", 5)))
            ), executor, Duration.ofMillis(10), 0.0, clock);

            throttler.execute("/api/heavy", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/heavy", () -> "ok").get(1, TimeUnit.SECONDS);

            var future = throttler.execute("/api/heavy", () -> "ok");
            Thread.sleep(50);
            assertThat(future.isDone()).isFalse();

            clock.advance(Duration.ofSeconds(3));
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        }

        @Test
        @DisplayName("weightOverrides로 동적 weight를 적용할 수 있다")
        void weightOverrideIsApplied() throws Exception {
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("POOL", 10, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/ticker", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("POOL", 2)))
            ), executor, Duration.ofMillis(10), 0.0, clock);

            throttler.execute("/api/ticker", () -> "ok", Map.of("POOL", 10))
                    .get(1, TimeUnit.SECONDS);

            var future = throttler.execute("/api/ticker", () -> "ok");
            Thread.sleep(50);
            assertThat(future.isDone()).isFalse();

            clock.advance(Duration.ofSeconds(3));
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("여러 풀에 동시 소비")
    class MultiplePoolConsumption {
        @Test
        @DisplayName("linkedLimits로 연결된 모든 풀에서 용량을 소비한다")
        void consumesFromAllLinkedPools() throws Exception {
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("WEIGHT", 100, Duration.ofSeconds(2)),
                    RateLimit.pool("ORDERS", 2, Duration.ofSeconds(2)),
                    RateLimit.endpoint("/api/inFlightOrder", Duration.ofMinutes(1), Integer.MAX_VALUE, 1, List.of(
                            new RateLimit.LinkedLimitWeightPair("WEIGHT", 4),
                            new RateLimit.LinkedLimitWeightPair("ORDERS", 1)))
            ), executor, Duration.ofMillis(10), 0.0, clock);

            throttler.execute("/api/inFlightOrder", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/inFlightOrder", () -> "ok").get(1, TimeUnit.SECONDS);

            var future = throttler.execute("/api/inFlightOrder", () -> "ok");
            Thread.sleep(50);
            assertThat(future.isDone()).isFalse();

            clock.advance(Duration.ofSeconds(3));
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("자기 자신 풀 소비")
    class SelfPoolConsumption {

        @Test
        @DisplayName("linkedLimits 없는 endpoint는 자기 자신의 풀에서 소비한다")
        void endpointWithoutLinkedLimitsUsesSelfPool() throws Exception {
            clock = new TestClock(Instant.now());
            throttler = new AsyncThrottlerImpl(List.of(
                    RateLimit.pool("/api/simple", 2, Duration.ofSeconds(2))
            ), executor, Duration.ofMillis(10), 0.0, clock);

            throttler.execute("/api/simple", () -> "ok").get(1, TimeUnit.SECONDS);
            throttler.execute("/api/simple", () -> "ok").get(1, TimeUnit.SECONDS);

            var future = throttler.execute("/api/simple", () -> "ok");
            Thread.sleep(50);
            assertThat(future.isDone()).isFalse();

            clock.advance(Duration.ofSeconds(3));
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        }
    }
}
