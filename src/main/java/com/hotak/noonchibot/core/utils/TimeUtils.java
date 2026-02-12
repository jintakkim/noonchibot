package com.hotak.noonchibot.core.utils;

public class TimeUtils {
    public static double getCurrentSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
