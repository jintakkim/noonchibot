package com.hotak.noonchibot.core.utils;

import java.time.Instant;

public class TimeUtils {
    public static double getCurrentSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    public static double getCurrentUnixTimestamp() {
        return Instant.now().toEpochMilli() / 1000.0;
    }
}
