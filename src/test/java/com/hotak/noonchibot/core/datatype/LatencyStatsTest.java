package com.hotak.noonchibot.core.datatype;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LatencyStatsTest {
    @Test
    @DisplayName("초기값이 올바르게 설정된다")
    void testInitialValues() {
        LatencyStats stats = new LatencyStats();

        assertEquals(0, stats.getCount());
        assertNull(stats.getMinDuration());
        assertEquals(Duration.ZERO, stats.getMaxDuration());
        assertEquals(Duration.ZERO, stats.getTotalDuration());
        assertTrue(stats.getRecentSamples().isEmpty());
    }

    @Test
    @DisplayName("단일 샘플이 정상적으로 기록된다")
    void testRecordSingleSample() {
        LatencyStats stats = new LatencyStats(100, 1);
        stats.record(Duration.ofMillis(5));

        assertEquals(1, stats.getCount());
        assertEquals(Duration.ofMillis(5), stats.getMinDuration());
        assertEquals(Duration.ofMillis(5), stats.getMaxDuration());
    }

    @Test
    @DisplayName("여러 샘플 기록 시 최소/최대값이 정상적으로 업데이트된다")
    void testRecordMultipleSamplesUpdatesMinMax() {
        LatencyStats stats = new LatencyStats(100, 1);

        stats.record(Duration.ofMillis(10));
        stats.record(Duration.ofMillis(5));
        stats.record(Duration.ofMillis(15));
        stats.record(Duration.ofMillis(8));

        assertEquals(4, stats.getCount());
        assertEquals(Duration.ofMillis(5), stats.getMinDuration());
        assertEquals(Duration.ofMillis(15), stats.getMaxDuration());
    }

    @Test
    @DisplayName("샘플링이 정상적으로 동작한다")
    void testSamplingBehavior() {
        LatencyStats stats = new LatencyStats(100, 10);

        for (int i = 0; i < 25; i++) {
            stats.record(Duration.ofMillis(1));
        }

        assertEquals(25, stats.getCount());
        assertEquals(2, stats.getRecentSamples().size());
    }

    @Test
    @DisplayName("롤링 윈도우 크기 제한이 정상적으로 동작한다")
    void testRollingWindowSizeLimit() {
        LatencyStats stats = new LatencyStats(5, 1);

        for (int i = 0; i < 10; i++) {
            stats.record(Duration.ofMillis(i));
        }

        assertEquals(5, stats.getRecentSamples().size());
    }

    @Test
    @DisplayName("데이터가 없을 때 minDuration이 null이다")
    void testInitialMinDurationIsNull() {
        LatencyStats stats = new LatencyStats();
        assertNull(stats.getMinDuration());
    }
}