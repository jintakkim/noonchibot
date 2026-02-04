package com.hotak.noonchibot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RealtimeClock implements Clock {
    private static final Logger log = LoggerFactory.getLogger(RealtimeClock.class);
    private final List<TimeIterator> iterators;
    private final Duration tickSize;
    private volatile boolean started = false;
    //반드시 runUntil에서만 업데이트되어야 한다.
    private volatile Instant currentTimestamp;

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
    @SuppressWarnings("BusyWait")
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
                //realtime mode 이기 떄문에 예외가 발생하더라도 무시하고 루프문 수행
                //따라서 별도로 timeIterator 에서 예외처리가 필요하다.
                log.error("예외 발생", e);
            }
        }
        stop();
    }

    private void executeTick(Instant timestamp) {
        for (TimeIterator iterator : iterators) {
            iterator.onTick(timestamp);
        }
    }

    private void stop() {
        for (TimeIterator iterator : iterators) {
            iterator.onStop();
        }
    }

    private void updateCurrentTimestamp() {
        currentTimestamp = Instant.now();
    }
}
