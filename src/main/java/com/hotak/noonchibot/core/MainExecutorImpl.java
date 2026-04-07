package com.hotak.noonchibot.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 봇에서의 모든 상태 변경(채결 발생, 오더북 업데이트, 잔고 업데이트 등), 전략 실행 등의 행동은 해당 Executor에서 실행된다.
 * 단일 스레드 기반이기 떄문에 모든 구성 요소는 병렬 안정성을 고려하지 않아도 된다.
 * 전략 실행시 도중 상태가 변경될 수 없기 떄문때 전략 실행시 팬텀 리드등의 문제를 고려하지 않아도 된다.
 *
 * ######### WARNING ############
 * 해당 Executor에는 시간이 많이 소요되는 IO BOUND 작업을 해서는 안된다.
 * 하나의 Task의 소요시간이 WARN_THRESHOLD을 초과하면 경고로그가 발생한다, KILL_THRESHOLD를 초과하면 강제 종료가 발생한다.
 *
 * todo: 강제 종료 발생시 재시작, 재시작시 복구 모드 구현
 */
@Slf4j
public class MainExecutorImpl implements SmartLifecycle, MainExecutor {
    private static final long WATCHDOG_INTERVAL_MS = 1000;
    private static final Duration WARN_THRESHOLD = Duration.ofMillis(100);
    private static final Duration KILL_THRESHOLD = Duration.ofSeconds(5);

    private volatile long taskStartNanos = -1;
    private final ExecutorService delegate = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchDog = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;

    @Override
    public void execute(Runnable command) {
        if (!running) {
            throw new IllegalStateException("MainExecutor is not running");
        }
        delegate.execute(() -> {
            taskStartNanos = System.nanoTime();
            try {
                command.run();
            } catch (Exception e) {
                log.error("Task failed", e);
            } finally {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - taskStartNanos);
                if (elapsed.compareTo(WARN_THRESHOLD) >= 0) {
                    log.warn("Task took {}ms, exceeds warn threshold", elapsed.toMillis());
                }
                taskStartNanos = -1;
            }
        });
    }

    @Override
    public void start() {
        watchDog.scheduleAtFixedRate(() -> {
            if(taskStartNanos == -1) return;
            Duration elapsed = Duration.ofNanos(System.nanoTime() - taskStartNanos);
            if(elapsed.compareTo(KILL_THRESHOLD) >= 0) {
                log.error("Task stuck for {}ms, shutting down", elapsed.toMillis());
                System.exit(1);
            }
        }, WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public void stop() {
        delegate.shutdown();
        watchDog.shutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        execute(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
