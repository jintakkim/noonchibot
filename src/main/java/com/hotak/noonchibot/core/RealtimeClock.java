package com.hotak.noonchibot.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class RealtimeClock implements Clock, SmartLifecycle {
    private final List<TimeIterator> iterators;
    private final Duration tickSize;
    private volatile boolean started = false;

    private volatile Instant currentTimestamp;
    private volatile Thread clockThread;


    public RealtimeClock(List<TimeIterator> iterators, Duration tickSize) {
        this.iterators = new CopyOnWriteArrayList<>(iterators);
        this.tickSize = tickSize;
    }

    @Override
    public void addIterator(TimeIterator iterator) {
        if(started) iterator.onStart(this, currentTimestamp);
        iterators.add(iterator);
    }

    @Override
    public void removeIterator(TimeIterator iterator) {
        if(iterators.remove(iterator) && started) {
            iterator.onStop();
        }
    }

    /**
     * 지정된 시간까지 실행 (블로킹)
     * @param endTime 종료 시간, null이면 무한 실행
     */
    @Override
    public void run(Instant endTime) {
        long tickMillis = tickSize.toMillis();
        updateCurrentTimestamp();
        if (!started) {
            for (TimeIterator iterator : iterators) {
                iterator.onStart(this, currentTimestamp);
            }
            started = true;
        }
        while (true) {
            try {
                updateCurrentTimestamp();
                if (endTime != null && currentTimestamp.isAfter(endTime)) {
                    break;
                }
                long nextTickTime = ((currentTimestamp.toEpochMilli() / tickMillis) + 1) * tickMillis;
                Thread.sleep(nextTickTime - currentTimestamp.toEpochMilli());
                executeTick(Instant.ofEpochMilli(nextTickTime));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("처리되지 못한 예외 발생", e);
            }
        }
        try {
            for (TimeIterator iterator : iterators) {
                iterator.onStop();
            }
        } catch (Exception e) {
            log.error("종료 작업중 예외 발생", e);
        } finally {
            started = false;
        }
    }

    private void executeTick(Instant timestamp) {
        for (TimeIterator iterator : iterators) {
            iterator.onTick(timestamp);
        }
    }

    @Override
    public void start() {
        clockThread = Thread.ofPlatform().name("realtime-clock").start(() -> run(null));
    }

    @Override
    public void stop() {
        if (clockThread != null) {
            clockThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return clockThread != null && clockThread.isAlive();
    }

    private void updateCurrentTimestamp() {
        currentTimestamp = Instant.now();
    }
}
