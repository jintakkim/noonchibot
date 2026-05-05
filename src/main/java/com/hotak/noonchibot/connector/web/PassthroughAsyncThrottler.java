package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import org.springframework.core.task.TaskExecutor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Rate limiting 없이 즉시 실행하는 throttler.
 *
 * 사용 시나리오:
 *   일회성 administrative 도구 (agent 등록 등) - rate limit 신경쓸 일 없음
 *   단위 테스트 - 결정적 동작 필요
 */
public class PassthroughAsyncThrottler implements AsyncThrottler {

    private final TaskExecutor executor;

    public PassthroughAsyncThrottler() {
        executor = Runnable::run;  // 호출 스레드에서 즉시 실행
    }

    @Override
    public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task) {
        return execute(limitId, task, Map.of());
    }

    @Override
    public <T> CompletableFuture<T> execute(
            String limitId,
            Supplier<T> task,
            Map<String, Integer> weightOverrides
    ) {
        return CompletableFuture.supplyAsync(task, executor);
    }
}