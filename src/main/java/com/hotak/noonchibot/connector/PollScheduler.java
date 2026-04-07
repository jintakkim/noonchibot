package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;

/**
 * websocket 연결 여부에 따라 주기적인 polling을 스케줄링한다.
 */
@RequiredArgsConstructor
public class PollScheduler {
    private static final Duration SHORT_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration LONG_POLL_INTERVAL = Duration.ofMinutes(2);
    private static final Duration TICK_INTERVAL_LIMIT = Duration.ofMinutes(1);

    private final WebsocketStatus websocketStatus;
    private final TaskScheduler scheduler;
    private volatile boolean running = false;

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
        if (lastRecvTime == null) return SHORT_POLL_INTERVAL;
        Duration elapsed = Duration.between(lastRecvTime, Instant.now());
        return elapsed.compareTo(TICK_INTERVAL_LIMIT) > 0
                ? SHORT_POLL_INTERVAL
                : LONG_POLL_INTERVAL;
    }
}