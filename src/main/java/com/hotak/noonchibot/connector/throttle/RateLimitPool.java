package com.hotak.noonchibot.connector.throttle;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;

@RequiredArgsConstructor
public class RateLimitPool {
    public final RateLimit rateLimit;
    private final Clock clock;
    private final Deque<TaskLog> logs = new ArrayDeque<>();

    /**
     * @return 해당 weight만큼 여유가 있는지
     */
    public synchronized boolean hasCapacity(int weight, double safetyMarginPct) {
        purgeExpired();

        int used = logs.stream()
                .mapToInt(TaskLog::weight)
                .sum();

        int effectiveLimit = Math.max(
                1,
                (int) Math.floor(
                        rateLimit.limit() * (1 - safetyMarginPct)
                )
        );

        return used + weight <= effectiveLimit;
    }

    public synchronized void record(int weight) {
        logs.addLast(new TaskLog(weight, clock.millis()));
    }

    private void purgeExpired() {
        long now = clock.millis();
        long windowMs = rateLimit.timeInterval().toMillis();
        while (!logs.isEmpty() && now - logs.peekFirst().timestamp() >= windowMs) {
            logs.pollFirst();
        }
    }

    private record TaskLog(int weight, long timestamp) {}
}
