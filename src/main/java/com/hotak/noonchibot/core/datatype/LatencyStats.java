package com.hotak.noonchibot.core.datatype;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class LatencyStats {
    private final int rollingWindowSize; //최근 평균 계산용 샘플 개수
    private final int sampleRate; //통계 반영 (실사용) 개수

    private long count = 0; //총 호출 메시지 수
    private double totalMs = 0.0; //누적 레이턴시 합계
    private double minMs = Double.POSITIVE_INFINITY; //최소 레이턴시
    private double maxMs = 0.0; //최대 레이턴시
    private final Deque<Double> recentSamples; //최근 레이턴시 저장
    private int sampleCounter = 0; //샘플 카운터

    public LatencyStats() {
        this(100, 10);
    }

    public LatencyStats(int rollingWindowSize, int sampleRate) {
        this.rollingWindowSize = rollingWindowSize;
        this.sampleRate = sampleRate;
        this.recentSamples = new ArrayDeque<>(rollingWindowSize);
    }

    public synchronized void record(double latencyMs) {
        count++;
        sampleCounter++;

        if (latencyMs < minMs) minMs = latencyMs;
        if (latencyMs > maxMs) maxMs = latencyMs;

        if (sampleCounter < sampleRate) {
            return;
        }

        sampleCounter = 0;
        totalMs += latencyMs * sampleRate;

        updateRollingWindow(latencyMs);
    }

    private void updateRollingWindow(double latencyMs) {
        if (recentSamples.size() >= rollingWindowSize) {
            recentSamples.pollFirst();
        }
        recentSamples.addLast(latencyMs);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();

        map.put("count", count);
        map.put("total_ms", totalMs);

        // min 값이 아직 초기 상태면 0 반환
        map.put("min_ms", minMs == Double.POSITIVE_INFINITY ? 0.0 : minMs);

        map.put("max_ms", maxMs);
        map.put("avg_ms", getAvgMs());
        map.put("recent_avg_ms", getRecentAvgMs());
        map.put("recent_samples_count", recentSamples.size());

        return map;
    }

    public double getAvgMs() {
        return count > 0 ? totalMs / count : 0.0;
    }

    public double getRecentAvgMs() {
        if (recentSamples.isEmpty()) return 0.0;

        return recentSamples
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    public long getCount() {
        return count;
    }
}

