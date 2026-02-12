package com.hotak.noonchibot.core.datatype;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LatencyStatsTest {

    @Test
    @DisplayName("초기값이 올바르게 설정된다")
    void testInitialValues() {
        LatencyStats stats = new LatencyStats();

        assertEquals(0, stats.getCount());
        Map<String, Object> result = stats.toMap();

        assertEquals(0.0, result.get("total_ms"));
        assertEquals(0.0, result.get("min_ms")); // Infinity는 0.0으로 변환
        assertEquals(0.0, result.get("max_ms"));
        assertEquals(0.0, result.get("avg_ms"));
        assertEquals(0.0, result.get("recent_avg_ms"));
        assertEquals(0, result.get("recent_samples_count"));
    }

    @Test
    @DisplayName("단일 샘플이 정상적으로 기록된다")
    void testRecordSingleSample() {
        LatencyStats stats = new LatencyStats(100, 1);
        stats.record(5.0);

        assertEquals(1, stats.getCount());
        Map<String, Object> result = stats.toMap();
        assertEquals(5.0, result.get("min_ms"));
        assertEquals(5.0, result.get("max_ms"));
    }

    @Test
    @DisplayName("여러 샘플 기록 시 최소/최대값이 정상적으로 업데이트된다")
    void testRecordMultipleSamplesUpdatesMinMax() {
        LatencyStats stats = new LatencyStats(100, 1);

        stats.record(10.0);
        stats.record(5.0);
        stats.record(15.0);
        stats.record(8.0);

        assertEquals(4, stats.getCount());
        Map<String, Object> result = stats.toMap();
        assertEquals(5.0, result.get("min_ms"));
        assertEquals(15.0, result.get("max_ms"));
    }

    @Test
    @DisplayName("샘플링이 정상적으로 동작한다")
    void testSamplingBehavior() {
        int sampleRate = 10;
        LatencyStats stats = new LatencyStats(100, sampleRate);

        // 25번 기록
        for (int i = 0; i < 25; i++) {
            stats.record(1.0);
        }

        assertEquals(25, stats.getCount());
        // 10번째, 20번째에만 저장되었으므로 최근 샘플 수는 2여야 함
        Map<String, Object> result = stats.toMap();
        assertEquals(2, result.get("recent_samples_count"));
    }

    @Test
    @DisplayName("전체 평균 계산이 정상적으로 동작한다")
    void testAvgMsCalculation() {
        LatencyStats stats = new LatencyStats(100, 1);

        stats.record(10.0);
        stats.record(20.0);
        stats.record(30.0);

        // (10+20+30) / 3 = 20.0
        assertEquals(20.0, stats.getAvgMs(), 0.001);
    }

    @Test
    @DisplayName("최근 평균(Rolling Window) 계산이 정상적으로 동작한다")
    void testRecentAvgMsCalculation() {
        LatencyStats stats = new LatencyStats(100, 1);

        for (int i = 1; i <= 5; i++) {
            stats.record((double) i); // 1, 2, 3, 4, 5
        }

        // 평균 = 15 / 5 = 3.0
        assertEquals(3.0, stats.getRecentAvgMs(), 0.001);
    }

    @Test
    @DisplayName("롤링 윈도우 크기 제한이 정상적으로 동작한다")
    void testRollingWindowSizeLimit() {
        int windowSize = 5;
        LatencyStats stats = new LatencyStats(windowSize, 1);

        // 윈도우 크기보다 많은 10개의 샘플 기록
        for (int i = 0; i < 10; i++) {
            stats.record((double) i);
        }

        // 최근 샘플은 마지막 5개(5,6,7,8,9)만 유지되어야 함
        Map<String, Object> result = stats.toMap();
        assertEquals(5, result.get("recent_samples_count"));

        // 최근 평균은 (5+6+7+8+9)/5 = 7.0
        assertEquals(7.0, stats.getRecentAvgMs(), 0.001);
    }

    @Test
    @DisplayName("toMap 태스트")
    void testToDictSerialization() {
        LatencyStats stats = new LatencyStats(100, 1);
        stats.record(5.0);
        stats.record(10.0);

        Map<String, Object> result = stats.toMap();

        assertTrue(result.containsKey("count"));
        assertTrue(result.containsKey("total_ms"));
        assertTrue(result.containsKey("min_ms"));
        assertTrue(result.containsKey("max_ms"));
        assertTrue(result.containsKey("avg_ms"));
        assertTrue(result.containsKey("recent_avg_ms"));
        assertTrue(result.containsKey("recent_samples_count"));

        assertEquals(2L, result.get("count"));
        assertEquals(5.0, result.get("min_ms"));
        assertEquals(10.0, result.get("max_ms"));
    }

    @Test
    @DisplayName("데이터가 없을 때 min_ms가 0.0으로 반환된다")
    void testToDictHandlesInfinity() {
        LatencyStats stats = new LatencyStats(); // 아무것도 기록 안 함

        Map<String, Object> result = stats.toMap();

        // 초기값 무한대(Infinity)가 0.0으로 잘 변환되었는지 확인
        assertEquals(0.0, result.get("min_ms"));
    }
}