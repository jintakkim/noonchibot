package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.OrderBookPairMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InFlightOrderBookPairMetricsTest {
    @Test
    @DisplayName("초기화 시 기본값들이 올바르게 설정되어야 한다")
    void testInitialization() {
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", Instant.now());

        assertEquals("BTC-USDT", metrics.getTradingPair());
        assertEquals(0, metrics.getDiffsProcessed());
        assertEquals(0, metrics.getDiffsRejected());
        assertEquals(0, metrics.getSnapshotsProcessed());
        assertEquals(0, metrics.getTradesProcessed());
        assertEquals(0, metrics.getTradesRejected());
    }

    @Test
    @DisplayName("분당 메시지 처리율 계산이 정확해야 한다")
    void testMessagesPerMinuteCalculation() {
        Instant start = Instant.now();
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", start);

        Instant now = start.plusSeconds(60);

        metrics.recordDiffProcessed(Duration.ofMillis(1), now);
        metrics.recordDiffProcessed(Duration.ofMillis(1), now);
        metrics.recordSnapshotProcessed(Duration.ofMillis(1), now);
        metrics.recordTradeProcessed(Duration.ofMillis(1), now);

        Map<String, Double> rates = metrics.getMessagesPerMinute(now);

        assertEquals(2.0, rates.get("diffs"), 0.01);
        assertEquals(1.0, rates.get("snapshots"), 0.01);
        assertEquals(1.0, rates.get("trades"), 0.01);
        assertEquals(4.0, rates.get("total"), 0.01);
    }

    @Test
    @DisplayName("경과 시간이 0일 때 분당 메시지 계산은 0을 반환해야 한다")
    void testMessagesPerMinuteZeroElapsed() {
        Instant now = Instant.now();
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", now);

        Map<String, Double> rates = metrics.getMessagesPerMinute(now);

        assertEquals(0.0, rates.get("diffs"));
        assertEquals(0.0, rates.get("total"));
    }

    @Test
    @DisplayName("recordDiffProcessed 호출 시 카운트와 타임스탬프가 업데이트된다")
    void testRecordDiffProcessed() {
        Instant start = Instant.now();
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", start);

        Instant now = start.plusSeconds(1);
        metrics.recordDiffProcessed(Duration.ofMillis(5), now);

        assertEquals(1, metrics.getDiffsProcessed());
        assertEquals(now, metrics.getLastDiffTimestamp());
    }

    @Test
    @DisplayName("incrementDiffsRejected 호출 시 카운트가 증가한다")
    void testIncrementDiffsRejected() {
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", Instant.now());

        metrics.incrementDiffsRejected();
        metrics.incrementDiffsRejected();

        assertEquals(2, metrics.getDiffsRejected());
    }
}