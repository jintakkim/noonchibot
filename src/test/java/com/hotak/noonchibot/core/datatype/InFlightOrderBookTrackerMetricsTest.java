package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.OrderBookPairMetrics;
import com.hotak.noonchibot.core.orderbook.OrderBookTrackerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InFlightOrderBookTrackerMetricsTest {
    private OrderBookTrackerMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new OrderBookTrackerMetrics();
    }

    @Test
    @DisplayName("초기화 시 기본값들이 올바르게 설정되어야 한다")
    void testInitialization() {
        assertEquals(0, metrics.getTotalDiffsProcessed());
        assertEquals(0, metrics.getTotalSnapshotsProcessed());
        assertEquals(0, metrics.getTotalTradesProcessed());
        assertTrue(metrics.getPerPairMetrics().isEmpty());
    }

    @Test
    @DisplayName("새로운 페어 메트릭 생성 및 기존 객체 반환 확인")
    void testGetOrCreatePairMetrics() {
        OrderBookPairMetrics pair1 = metrics.getOrCreatePairMetrics("BTC-USDT");
        assertNotNull(pair1);

        OrderBookPairMetrics pair2 = metrics.getOrCreatePairMetrics("BTC-USDT");
        assertSame(pair1, pair2);
    }

    @Test
    @DisplayName("페어 삭제가 정상적으로 동작해야 한다")
    void testRemovePairMetrics() {
        metrics.getOrCreatePairMetrics("BTC-USDT");
        metrics.getOrCreatePairMetrics("ETH-USDT");

        metrics.removePairMetrics("BTC-USDT");

        assertFalse(metrics.getPerPairMetrics().containsKey("BTC-USDT"));
        assertTrue(metrics.getPerPairMetrics().containsKey("ETH-USDT"));
        assertEquals(1, metrics.getPerPairMetrics().size());
    }

    @Test
    @DisplayName("분당 처리율 계산이 정확해야 한다")
    void testMessagesPerMinute() {
        Instant start = Instant.now();
        metrics.setTrackerStartTime(start);

        for (int i = 0; i < 60; i++) {
            metrics.recordDiffProcessed(Duration.ofMillis(1));
        }

        Instant now = start.plusSeconds(60);
        Map<String, Double> rates = metrics.getMessagesPerMinute(now);

        assertEquals(60.0, rates.get("diffs"), 0.01);
        assertEquals(0.0, rates.get("snapshots"), 0.01);
        assertEquals(0.0, rates.get("trades"), 0.01);
        assertEquals(60.0, rates.get("total"), 0.01);
    }

    @Test
    @DisplayName("경과 시간이 0일 때 분당 처리율은 0을 반환해야 한다")
    void testMessagesPerMinuteZeroElapsed() {
        Instant now = Instant.now();
        metrics.setTrackerStartTime(now);

        Map<String, Double> rates = metrics.getMessagesPerMinute(now);

        assertEquals(0.0, rates.get("diffs"));
        assertEquals(0.0, rates.get("total"));
    }

    @Test
    @DisplayName("increment 메서드들이 정상적으로 동작해야 한다")
    void testIncrements() {
        metrics.incrementTotalDiffsQueued();
        metrics.incrementTotalDiffsQueued();
        metrics.incrementTotalDiffsRejected();
        metrics.incrementTotalSnapshotsRejected();
        metrics.incrementTotalTradesRejected();

        assertEquals(2, metrics.getTotalDiffsQueued());
        assertEquals(1, metrics.getTotalDiffsRejected());
        assertEquals(1, metrics.getTotalSnapshotsRejected());
        assertEquals(1, metrics.getTotalTradesRejected());
    }
}