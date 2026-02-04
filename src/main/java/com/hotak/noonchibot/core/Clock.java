package com.hotak.noonchibot.core;

import java.time.Instant;

public interface Clock {
    void addIterator(TimeIterator iterator);
    void removeIterator(TimeIterator iterator);
    void runUntil(Instant endTime);
}
