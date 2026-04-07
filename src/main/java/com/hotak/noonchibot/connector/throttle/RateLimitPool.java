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
        purgeExpired(safetyMarginPct);
        int used = logs.stream().mapToInt(TaskLog::weight).sum();
        return used + weight <= rateLimit.limit();
    }

    public synchronized void record(int weight) {
        logs.addLast(new TaskLog(weight, clock.millis()));
    }

    private void purgeExpired(double safetyMarginPct) {
        long now = clock.millis();
        long effectiveWindowMs = (long) (rateLimit.timeInterval().toMillis() * (1 - safetyMarginPct));
        while (!logs.isEmpty() && now - logs.peekFirst().timestamp() > effectiveWindowMs) {
            logs.pollFirst();
        }
    }

    private record TaskLog(int weight, long timestamp) {}
}
