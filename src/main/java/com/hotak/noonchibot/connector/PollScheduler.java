package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;

/**
 * websocket 연결 여부에 따라 주기적인 polling을 스케줄링한다.
 */
public class PollScheduler {
    private static final Duration DEFAULT_SHORT_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_LONG_POLL_INTERVAL = Duration.ofMinutes(2);
    private static final Duration DEFAULT_TICK_INTERVAL_LIMIT = Duration.ofMinutes(1);

    private final WebsocketStatus websocketStatus;
    private final TaskScheduler scheduler;
    /**
     *
     */
    private final Duration shortPollInterval;
    private final Duration longPollInterval;
    /**
     * 마지막 websocket 값 수신이 해당 값을 초과했다면 shortPollInterval 마다 poll, 초과하지 않았다면 longPollInterval 마다 poll
     *
     */
    private final Duration tickIntervalLimit;
    private volatile boolean running = false;

    public PollScheduler(
            WebsocketStatus websocketStatus,
            TaskScheduler scheduler,
            Duration shortPollInterval,
            Duration longPollInterval,
            Duration tickIntervalLimit
    ) {
        this.websocketStatus = websocketStatus;
        this.scheduler = scheduler;
        this.shortPollInterval = shortPollInterval;
        this.longPollInterval = longPollInterval;
        this.tickIntervalLimit = tickIntervalLimit;
    }

    public PollScheduler(
            WebsocketStatus websocketStatus,
            TaskScheduler scheduler
    ) {
        this(
                websocketStatus,
                scheduler,
                DEFAULT_SHORT_POLL_INTERVAL,
                DEFAULT_LONG_POLL_INTERVAL,
                DEFAULT_TICK_INTERVAL_LIMIT
        );
    }

    public void start(Runnable task) {
        running = true;
        scheduleNext(task);
    }

    public void stop() {
        running = false;
    }

    private void scheduleNext(Runnable task) {
        if (!running) return;
        scheduler.schedule(() -> {
                try {
                    task.run();
                } finally {
                    scheduleNext(task);
                }
            }, Instant.now().plus(getPollInterval())
        );
    }

    private Duration getPollInterval() {
        Instant lastRecvTime = websocketStatus.getLastRecvTime();
        if (lastRecvTime == null) return shortPollInterval;
        Duration elapsed = Duration.between(lastRecvTime, Instant.now());
        return elapsed.compareTo(tickIntervalLimit) > 0
                ? shortPollInterval
                : longPollInterval;
    }
}