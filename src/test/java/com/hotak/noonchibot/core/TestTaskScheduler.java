package com.hotak.noonchibot.core;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 테스트용 TaskScheduler.
 *
 * 실제 스케줄링을 하지 않고, 어떤 task가 어떤 방식(주기/지연/일회성)으로 등록됐는지만
 * 메모리에 기록한다. 등록된 task의 Runnable은 호출되지 않는다.
 *
 * task의 동작 자체는 해당 task의 단위 테스트에서 검증하고, 이 mock은 스케줄링 등록 사실과
 * 등록 인자(period, delay, startTime 등)만 확인하는 데 사용한다.
 */
public class TestTaskScheduler implements TaskScheduler {

    private final List<ScheduledTask> scheduledTasks = new ArrayList<>();

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        return record(new ScheduledTask(task, ScheduleKind.TRIGGER, trigger, null, null, null));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return record(new ScheduledTask(task, ScheduleKind.ONE_SHOT, null, null, null, startTime));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        return record(new ScheduledTask(task, ScheduleKind.FIXED_RATE, null, period, null, startTime));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return record(new ScheduledTask(task, ScheduleKind.FIXED_RATE, null, period, null, null));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        return record(new ScheduledTask(task, ScheduleKind.FIXED_DELAY, null, null, delay, startTime));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return record(new ScheduledTask(task, ScheduleKind.FIXED_DELAY, null, null, delay, null));
    }

    private ScheduledFuture<?> record(ScheduledTask entry) {
        scheduledTasks.add(entry);
        return entry.future;
    }

    // ===== 검증 API =====

    public List<ScheduledTask> scheduledTasks() {
        return List.copyOf(scheduledTasks);
    }

    public ScheduledTask onlyScheduledTask() {
        if (scheduledTasks.size() != 1) {
            throw new AssertionError("Expected exactly 1 scheduled task, found " + scheduledTasks.size());
        }
        return scheduledTasks.getFirst();
    }

    public int activeTaskCount() {
        return (int) scheduledTasks.stream()
                .filter(t -> !t.future.isCancelled())
                .count();
    }

    public void clear() {
        scheduledTasks.clear();
    }

    // ===== 내부 모델 =====

    public enum ScheduleKind { ONE_SHOT, FIXED_RATE, FIXED_DELAY, TRIGGER }

    public static final class ScheduledTask {
        private final Runnable task;
        private final ScheduleKind kind;
        private final Trigger trigger;       // TRIGGER 모드일 때만
        private final Duration period;       // FIXED_RATE 일 때만
        private final Duration delay;        // FIXED_DELAY 일 때만
        private final Instant startTime;     // 명시된 경우만
        private final SimpleScheduledFuture future = new SimpleScheduledFuture();

        ScheduledTask(Runnable task, ScheduleKind kind, Trigger trigger,
                      Duration period, Duration delay, Instant startTime) {
            this.task = task;
            this.kind = kind;
            this.trigger = trigger;
            this.period = period;
            this.delay = delay;
            this.startTime = startTime;
        }

        public Runnable task() { return task; }
        public ScheduleKind kind() { return kind; }
        public Trigger trigger() { return trigger; }
        public Duration period() { return period; }
        public Duration delay() { return delay; }
        public Instant startTime() { return startTime; }
        public boolean isCancelled() { return future.isCancelled(); }
    }

    /**
     * 등록 정보 추적용 ScheduledFuture. cancel만 의미가 있고 get/getDelay 등은 no-op.
     */
    private static final class SimpleScheduledFuture implements ScheduledFuture<Object> {
        private volatile boolean cancelled = false;
        @Override public boolean cancel(boolean mayInterruptIfRunning) { cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return cancelled; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed o) { return 0; }
    }
}