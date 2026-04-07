package com.hotak.noonchibot.core;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * 동기 실행
 */
public class TestMainExecutor implements MainExecutor {
    @Override
    public Future<?> submit(Runnable task) {
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        try {
            T result = task.call();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
