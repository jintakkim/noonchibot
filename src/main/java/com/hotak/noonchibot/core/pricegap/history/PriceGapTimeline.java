package com.hotak.noonchibot.core.pricegap.history;

import java.time.Duration;

public enum PriceGapTimeline {
    ONE_HOUR(Duration.ofHours(1), Duration.ofMinutes(1)),
    FOUR_HOURS(Duration.ofHours(4), Duration.ofMinutes(5)),
    ONE_DAY(Duration.ofDays(1), Duration.ofMinutes(15)),
    SEVEN_DAYS(Duration.ofDays(7), Duration.ofHours(1));

    private final Duration window;
    private final Duration bucketSize;

    PriceGapTimeline(Duration window, Duration bucketSize) {
        this.window = window;
        this.bucketSize = bucketSize;
    }

    public Duration window() {
        return window;
    }

    public Duration bucketSize() {
        return bucketSize;
    }
}
