package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * thread-safe
 * 거래소 서버 시간과 로컬 시간의 차이를 추적해서, 거래소가 요구하는 타임스탬프 기반 서명을 정확하게 만들기 위한 객체
 * 서버 offset 계산에는 정확한 시간 흐름만 필요하기 때문에 os의 ntp 점프 문제를 고려하여 System.currentTimeMillis() 대신 System.nanoTime()을 사용한다.
 */
@Slf4j
@RequiredArgsConstructor
public class TimeSynchronizer {
    private static final int MAX_SAMPLES = 5;
    private static final Duration UPDATE_INTERVAL = Duration.ofMinutes(30);

    private volatile List<Double> timeOffsetSamples = List.of();
    private final ServerTimeProvider serverTimeProvider;
    private final TaskScheduler taskScheduler;

    /**
     * @return ms 단위 반환
     */
    public long serverTime() {
        return (long) (getSystemMs() + timeOffsetMs());
    }

    private double timeOffsetMs() {
        if (timeOffsetSamples.isEmpty()) {
            // 샘플 없으면 거래소 api 시간 대신 OS time을 대신 사용
            log.warn("No server time samples available, falling back to local clock");
            return System.currentTimeMillis() - getSystemMs();
        }
        return (median(timeOffsetSamples) + weightedAverage(timeOffsetSamples)) / 2.0;
    }

    public void scheduleUpdate() {
        taskScheduler.scheduleAtFixedRate(() -> {
            try {
                updateServerTimeOffset();
            } catch (Exception e) {
                log.error("Failed to update server time offset", e);
            }
        }, UPDATE_INTERVAL);
    }

    void updateServerTimeOffset() {
        double localBeforeMs = getSystemMs();
        long serverTimeMs = serverTimeProvider.getServerTimeMs();
        double localAfterMs = getSystemMs();
        double localMidpointMs = (localBeforeMs + localAfterMs) / 2.0;
        double offsetMs = serverTimeMs - localMidpointMs;
        addSample(offsetMs);
    }

    private synchronized void addSample(double offsetMs) {
        var next = new ArrayList<>(timeOffsetSamples);
        if (next.size() >= MAX_SAMPLES) {
            next.removeFirst();
        }
        next.add(offsetMs);
        this.timeOffsetSamples = next; //객체 참조 변경
    }

    private static double median(List<Double> samples) {
        var sorted = samples.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0
                : sorted.get(mid);
    }

    private static double weightedAverage(List<Double> samples) {
        double weightedSum = 0;
        double weightTotal = 0;
        for (int i = 0; i < samples.size(); i++) {
            int weight = 2 * i + 1;
            weightedSum += samples.get(i) * weight;
            weightTotal += weight;
        }
        return weightedSum / weightTotal;
    }

    private static double getSystemMs() {
        return System.nanoTime() / 1e6;
    }
}
