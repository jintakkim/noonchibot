package com.hotak.noonchibot.core.datatype;

import lombok.Getter;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Getter
public class LatencyStats {
    private final int rollingWindowSize; //최근 평균 계산용 샘플 개수
    private final int sampleRate; //통계 반영 (실사용) 개수

    private long count = 0; //총 호출 메시지 수

    private Duration totalDuration = Duration.ZERO;
    private Duration minDuration = null;
    private Duration maxDuration = Duration.ZERO;

    private final Deque<Duration> recentSamples; //최근 레이턴시 저장
    private int sampleCounter = 0; //샘플 카운터

    public LatencyStats() {
        this(100, 10);
    }

    public LatencyStats(int rollingWindowSize, int sampleRate) {
        this.rollingWindowSize = rollingWindowSize;
        this.sampleRate = sampleRate;
        this.recentSamples = new ArrayDeque<>(rollingWindowSize);
    }

    public synchronized void record(Duration latency) {
        if (latency == null) return;

        count++;
        sampleCounter++;

        if (minDuration == null || latency.compareTo(minDuration) < 0) {
            minDuration = latency;
        }
        if (latency.compareTo(maxDuration) > 0) {
            maxDuration = latency;
        }

        if (sampleCounter < sampleRate) {
            return;
        }

        sampleCounter = 0;

        Duration sampledLatency = latency.multipliedBy(sampleRate);
        totalDuration = totalDuration.plus(sampledLatency);

        updateRollingWindow(latency);
    }

    private void updateRollingWindow(Duration latency) {
        if (recentSamples.size() >= rollingWindowSize) {
            recentSamples.pollFirst();
        }
        recentSamples.addLast(latency);
    }
}