package com.hotak.noonchibot.connector.throttle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class AsyncThrottlerImpl implements AsyncThrottler {
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMillis(100);
    private static final double DEFAULT_SAFETY_MARGIN_PCT = 0.05;

    private final Map<String, RateLimitPool> pools;
    private final TaskExecutor executor;
    private final Duration retryInterval;
    private final double safetyMarginPct;

    public AsyncThrottlerImpl(List<RateLimit> rateLimits, TaskExecutor executor, Duration retryInterval, double safetyMarginPct) {
        this.executor = executor;
        this.retryInterval = retryInterval;
        this.safetyMarginPct = safetyMarginPct;
        // 전부 pool로
        this.pools = rateLimits.stream().collect(toMap(RateLimit::limitId, RateLimitPool::new));
    }

    public AsyncThrottlerImpl(List<RateLimit> rateLimits, TaskExecutor executor) {
        this(rateLimits, executor, DEFAULT_RETRY_INTERVAL, DEFAULT_SAFETY_MARGIN_PCT);
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
            waitForCapacity(consumes);
            recordAll(consumes);
            return task.get();
        }, executor);
    }

    private void waitForCapacity(List<PoolConsume> consumes) {
        while (!consumes.stream().allMatch(c -> c.pool().hasCapacity(c.weight(), safetyMarginPct))) {
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void recordAll(List<PoolConsume> consumes) {
        consumes.forEach(c -> c.pool().record(c.weight()));
    }

    private record PoolConsume(RateLimitPool pool, int weight) {
    }
}
