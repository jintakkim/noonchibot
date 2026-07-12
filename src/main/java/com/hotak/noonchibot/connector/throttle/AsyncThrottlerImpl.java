package com.hotak.noonchibot.connector.throttle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    private final Object capacityLock = new Object();

    public AsyncThrottlerImpl(
            List<RateLimit> rateLimits,
            TaskExecutor executor,
            Duration retryInterval,
            double safetyMarginPct,
            Clock clock
    ) {
        this.executor = executor;
        this.retryInterval = retryInterval;
        this.safetyMarginPct = safetyMarginPct;
        this.pools = rateLimits.stream()
                .collect(toMap(
                        RateLimit::limitId,
                        rateLimit -> new RateLimitPool(rateLimit, clock)
                ));
    }

    public AsyncThrottlerImpl(List<RateLimit> rateLimits, TaskExecutor executor) {
        this(
                rateLimits,
                executor,
                DEFAULT_RETRY_INTERVAL,
                DEFAULT_SAFETY_MARGIN_PCT,
                Clock.systemUTC()
        );
    }

    @Override
    public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task) {
        return execute(limitId, task, new HashMap<>());
    }

    @Override
    public <T> CompletableFuture<T> execute(
            String limitId,
            Supplier<T> task,
            Map<String, Integer> weightOverrides
    ) {
        return CompletableFuture.supplyAsync(() -> {
            RateLimitPool rateLimitPool = pools.get(limitId);

            if (rateLimitPool == null) {
                log.warn(
                        "Unknown limit id: {}, executing without rate limiting",
                        limitId
                );
                return task.get();
            }

            List<PoolConsume> consumes = resolveConsumes(
                    rateLimitPool,
                    weightOverrides
            );

            waitForCapacityAndRecord(limitId, consumes);

            return task.get();
        }, executor);
    }

    private List<PoolConsume> resolveConsumes(RateLimitPool rateLimitPool, Map<String, Integer> weightOverrides) {
        List<PoolConsume> consumes = new ArrayList<>();

        for (RateLimit.LinkedLimitWeightPair pair
                : rateLimitPool.rateLimit.linkedLimits()) {
            RateLimitPool matchedPool = pools.get(pair.limitId());

            if (matchedPool == null) {
                continue;
            }

            int weight = weightOverrides.getOrDefault(
                    pair.limitId(),
                    pair.weight()
            );

            consumes.add(new PoolConsume(matchedPool, weight));
        }

        int endpointWeight = weightOverrides.getOrDefault(
                rateLimitPool.rateLimit.limitId(),
                rateLimitPool.rateLimit.weight()
        );

        consumes.add(new PoolConsume(rateLimitPool, endpointWeight));

        return consumes;
    }

    private void waitForCapacityAndRecord(String limitId, List<PoolConsume> consumes) {
        long waitStartedAt = System.nanoTime();
        boolean warned = false;

        while (!tryConsumeAll(consumes)) {
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(
                        "Interrupted while waiting for rate limit capacity: "
                                + limitId,
                        e
                );
            }
            Duration waited = Duration.ofNanos(
                    System.nanoTime() - waitStartedAt
            );
            if (!warned
                    && waited.compareTo(SLOW_WAIT_WARNING_THRESHOLD) >= 0) {
                warned = true;
                log.warn(
                        "Throttler wait exceeded threshold: "
                                + "limitId={}, waitedMs={}, consumes={}",
                        limitId,
                        waited.toMillis(),
                        describeConsumes(consumes)
                );
            }
        }
    }

    private boolean tryConsumeAll(List<PoolConsume> consumes) {
        synchronized (capacityLock) {
            for (PoolConsume consume : consumes) {
                boolean available = consume.pool().hasCapacity(
                        consume.weight(),
                        safetyMarginPct
                );
                if (!available) {
                    return false;
                }
            }
            for (PoolConsume consume : consumes) {
                consume.pool().record(consume.weight());
            }
            return true;
        }
    }

    private String describeConsumes(List<PoolConsume> consumes) {
        return consumes.stream()
                .map(consume ->
                        consume.pool().rateLimit.limitId()
                                + ":"
                                + consume.weight()
                )
                .toList()
                .toString();
    }

    private record PoolConsume(RateLimitPool pool, int weight) {
    }
}
