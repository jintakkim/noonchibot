package com.hotak.noonchibot.connector.throttle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class AsyncThrottlerImpl implements AsyncThrottler {
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMillis(100);
    private static final Duration SLOW_WAIT_WARNING_THRESHOLD = Duration.ofSeconds(1);
    private static final double DEFAULT_SAFETY_MARGIN_PCT = 0.05;

    private final Map<String, RateLimitPool> pools;
    private final TaskExecutor executor;
    private final Duration retryInterval;
    private final double safetyMarginPct;

    public AsyncThrottlerImpl(List<RateLimit> rateLimits, TaskExecutor executor, Duration retryInterval, double safetyMarginPct, Clock clock) {
        this.executor = executor;
        this.retryInterval = retryInterval;
        this.safetyMarginPct = safetyMarginPct;
        // 전부 pool로
        this.pools = rateLimits.stream().collect(toMap(RateLimit::limitId, rateLimit -> new RateLimitPool(rateLimit, clock)));
    }

    public AsyncThrottlerImpl(List<RateLimit> rateLimits, TaskExecutor executor) {
        this(rateLimits, executor, DEFAULT_RETRY_INTERVAL, DEFAULT_SAFETY_MARGIN_PCT, Clock.systemUTC());
    }

    @Override
    public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task) {
        return execute(limitId, task, new HashMap<>());
    }

    @Override
    public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task, Map<String, Integer> weightOverrides) {
        return CompletableFuture.supplyAsync(() -> {
            RateLimitPool rateLimitPool = pools.get(limitId);
            if (rateLimitPool == null) {
                log.warn("Unknown limit id: {}, executing without rate limiting", limitId);
                return task.get();
            }
            List<PoolConsume> consumes = new ArrayList<>();
            for (RateLimit.LinkedLimitWeightPair pair : rateLimitPool.rateLimit.linkedLimits()) {
                RateLimitPool matched = pools.get(pair.limitId());
                int weight = weightOverrides.getOrDefault(pair.limitId(), pair.weight());
                if (matched != null) consumes.add(new PoolConsume(matched, weight));
            }
            consumes.add(new PoolConsume(rateLimitPool, weightOverrides.getOrDefault(limitId, rateLimitPool.rateLimit.weight())));
            waitForCapacity(limitId, consumes);
            recordAll(consumes);
            return task.get();
        }, executor);
    }

    private void waitForCapacity(String limitId, List<PoolConsume> consumes) {
        long waitStartedAt = System.nanoTime();
        boolean warned = false;
        while (!consumes.stream().allMatch(c -> c.pool().hasCapacity(c.weight(), safetyMarginPct))) {
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Duration waited = Duration.ofNanos(System.nanoTime() - waitStartedAt);
            if (!warned && waited.compareTo(SLOW_WAIT_WARNING_THRESHOLD) >= 0) {
                warned = true;
                log.warn(
                        "Throttler wait exceeded threshold: limitId={}, waitedMs={}, consumes={}",
                        limitId,
                        waited.toMillis(),
                        describeConsumes(consumes)
                );
            }
        }
    }

    private void recordAll(List<PoolConsume> consumes) {
        consumes.forEach(c -> c.pool().record(c.weight()));
    }

    private String describeConsumes(List<PoolConsume> consumes) {
        return consumes.stream()
                .map(consume -> consume.pool().rateLimit.limitId() + ":" + consume.weight())
                .toList()
                .toString();
    }

    private record PoolConsume(RateLimitPool pool, int weight) {
    }
}
