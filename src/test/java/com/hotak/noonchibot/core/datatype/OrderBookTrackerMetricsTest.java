package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.OrderBookPairMetrics;
import com.hotak.noonchibot.core.orderbook.OrderBookTrackerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTrackerMetricsTest {

    private OrderBookTrackerMetrics metrics;

    @BeforeEach
    void setUp() {
        // 시작 시간을 0으로 초기화 (계산 편의를 위해)
        metrics = new OrderBookTrackerMetrics(0.0);
    }

    @Test
    @DisplayName("초기화 시 toMap을 통해 기본값들을 확인한다")
    void testInitialization() {
        Map<String, Object> result = metrics.toMap();

        assertEquals(0L, result.get("total_diffs_processed"));
        assertEquals(0L, result.get("total_snapshots_processed"));
        assertEquals(0L, result.get("total_trades_processed"));

        Map<?, ?> perPair = (Map<?, ?>) result.get("per_pair_metrics");
        assertTrue(perPair.isEmpty());
    }

    @Test
    @DisplayName("새로운 페어 메트릭 생성 및 기존 객체 반환 확인")
    void testGetOrCreatePairMetrics() {
        OrderBookPairMetrics pair1 = metrics.getOrCreatePairMetrics("BTC-USDT");
        assertNotNull(pair1);

        OrderBookPairMetrics pair2 = metrics.getOrCreatePairMetrics("BTC-USDT");

        // 객체 동일성 확인 (Python의 assertIs)
        assertSame(pair1, pair2);
    }

    @Test
    @DisplayName("페어 삭제가 정상적으로 동작해야 한다")
    void testRemovePairMetrics() {
        metrics.getOrCreatePairMetrics("BTC-USDT");
        metrics.getOrCreatePairMetrics("ETH-USDT");

        metrics.removePairMetrics("BTC-USDT");

        Map<String, Object> result = metrics.toMap();
        Map<?, ?> perPair = (Map<?, ?>) result.get("per_pair_metrics");

        assertEquals(1, perPair.size());
        assertFalse(perPair.containsKey("BTC-USDT"));
        assertTrue(perPair.containsKey("ETH-USDT"));
    }

    @Test
    @DisplayName("Reflection을 사용해 강제로 값을 넣고 분당 처리율을 계산한다")
    void testMessagesPerMinuteGlobal() throws Exception {
        // 파이썬의 metrics.total_diffs_processed = 600 시뮬레이션
        setPrivateField(metrics, "trackerStartTime", 100.0);
        setPrivateField(metrics, "totalDiffsProcessed", 600L);
        setPrivateField(metrics, "totalSnapshotsProcessed", 6L);
        setPrivateField(metrics, "totalTradesProcessed", 300L);

        // 60초 경과 시점 (160.0)
        Map<String, Double> rates = metrics.getMessagesPerMinute(160.0);

        assertEquals(600.0, rates.get("diffs"));
        assertEquals(6.0, rates.get("snapshots"));
        assertEquals(300.0, rates.get("trades"));
        assertEquals(906.0, rates.get("total"));
    }

    /**
     * 자바의 private 필드에 강제로 값을 넣는 헬퍼 메서드 (Reflection)
     */
    private void setPrivateField(Object object, String fieldName, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true); // private 무시
        field.set(object, value);
    }
}