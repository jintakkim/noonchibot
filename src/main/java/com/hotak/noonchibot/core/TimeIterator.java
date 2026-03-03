package com.hotak.noonchibot.core;

import lombok.Getter;

import java.time.Instant;

@Getter
public class TimeIterator {
    private volatile Clock clock;
    private volatile Instant currentTimestamp;

    public void onStart(Clock clock, Instant timestamp) {
        this.clock = clock;
        this.currentTimestamp = timestamp;
    }

    public void onTick(Instant timestamp) {
        this.currentTimestamp = timestamp;
    }

    public void onStop() {
        this.clock = null;
        this.currentTimestamp = null;
    }
}