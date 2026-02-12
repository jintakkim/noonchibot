package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.OrderBookPairMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookPairMetricsTest {

    @Test
    @DisplayName("초기화 시 기본값들이 올바르게 설정되어야 한다")
    void testInitialization() {
        // 생성 시점의 시간(0.0)을 인자로 전달
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", 0.0);

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
        // Given: 100초에 시작
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", 100.0);

        // 데이터 주입 (Setter 사용)
        metrics.setDiffsProcessed(120);
        metrics.setSnapshotsProcessed(2);
        metrics.setTradesProcessed(60);

        // When: 현재 시간 160.0 (60초 경과)
        Map<String, Double> rates = metrics.getMessagesPerMinute(160.0);

        // Then
        assertEquals(120.0, rates.get("diffs"));
        assertEquals(2.0, rates.get("snapshots"));
        assertEquals(60.0, rates.get("trades"));
        assertEquals(182.0, rates.get("total"));
    }

    @Test
    @DisplayName("경과 시간이 0일 때 분당 메시지 계산은 0을 반환해야 한다")
    void testMessagesPerMinuteZeroElapsed() {
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("BTC-USDT", 0.0);
        metrics.setTrackingStartTime(0.0);

        Map<String, Double> rates = metrics.getMessagesPerMinute(0.0);

        assertEquals(0.0, rates.get("diffs"));
        assertEquals(0.0, rates.get("total"));
    }

    @Test
    @DisplayName("toMap 호출 시 필요한 모든 필드가 포함되어야 한다")
    void testToMapSerialization() {
        OrderBookPairMetrics metrics = new OrderBookPairMetrics("ETH-USDT", 100.0);
        metrics.setDiffsProcessed(100);
        metrics.setTradesProcessed(50);

        // 자바에서는 toDict 대신 toMap으로 명명하는 것이 관례입니다.
        Map<String, Object> result = metrics.toMap(160.0);

        assertEquals("ETH-USDT", result.get("trading_pair"));
        assertEquals(100L, result.get("diffs_processed"));
        assertEquals(50L, result.get("trades_processed"));

        // 키 존재 여부 확인
        assertTrue(result.containsKey("messages_per_minute"));
        assertTrue(result.containsKey("diff_latency"));
        assertTrue(result.containsKey("snapshot_latency"));
        assertTrue(result.containsKey("trade_latency"));
    }
}
