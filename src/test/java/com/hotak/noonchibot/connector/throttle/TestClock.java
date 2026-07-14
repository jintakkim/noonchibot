package com.hotak.noonchibot.connector.throttle;

import java.time.*;

public class TestClock extends Clock {
    private volatile Instant instant;
    private final ZoneId zone = ZoneOffset.UTC;

    public TestClock(Instant initial) {
        this.instant = initial;
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override public Instant instant() { return instant; }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
