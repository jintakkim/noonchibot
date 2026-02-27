package com.hotak.noonchibot.connector.throttle;

import java.util.ArrayDeque;
import java.util.Deque;

public class RateLimitPool {
    public final RateLimit rateLimit;
    private final Deque<TaskLog> logs = new ArrayDeque<>();

    public RateLimitPool(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    /**
     * @return 해당 weight만큼 여유가 있는지
     */
    public synchronized boolean hasCapacity(int weight, double safetyMarginPct) {
        purgeExpired(safetyMarginPct);
        int used = logs.stream().mapToInt(TaskLog::weight).sum();
        return used + weight <= rateLimit.limit();
    }

    public synchronized void record(int weight) {
        logs.addLast(new TaskLog(weight, System.currentTimeMillis()));
    }

    private void purgeExpired(double safetyMarginPct) {
        long now = System.currentTimeMillis();
        long effectiveWindowMs = (long) (rateLimit.timeInterval().toMillis() * (1 - safetyMarginPct));
        while (!logs.isEmpty() && now - logs.peekFirst().timestamp() > effectiveWindowMs) {
            logs.pollFirst();
        }
    }

    private record TaskLog(int weight, long timestamp) {}
}
