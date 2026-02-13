package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@ExtendWith(MockitoExtension.class)
public class OrderBookTrackerTest {

    @Mock
    private OrderBookTrackerDataSource dataSource;

    private OrderBookTracker tracker;
    private List<String> tradingPairs;

    private Map<String, OrderBook> orderBooks;
    private Map<String, BlockingQueue<OrderBookMessage>> trackingQueues;
    private Map<String, Deque<OrderBookMessage>> savedMessageQueues;
    private BlockingQueue<OrderBookMessage> diffStream;
    private BlockingQueue<OrderBookMessage> snapshotStream;
    private BlockingQueue<OrderBookMessage> tradeStream;

    @BeforeEach
    void setUp() throws Exception {
        tradingPairs = new ArrayList<>(List.of("BTC-USDT", "ETH-USDT"));
        tracker = new OrderBookTracker(dataSource, tradingPairs, "test-domain");

        orderBooks = getPrivateField(tracker, "orderBooks");
        trackingQueues = getPrivateField(tracker, "trackingQueues");
        diffStream = getPrivateField(tracker, "diffStream");
        snapshotStream = getPrivateField(tracker, "snapshotStream");
        tradeStream = getPrivateField(tracker, "tradeStream");

        for (String pair : tradingPairs) {
            OrderBook mockBook = mock(OrderBook.class);
            orderBooks.put(pair, mockBook);
            trackingQueues.put(pair, new LinkedBlockingQueue<>());
        }
    }

    @AfterEach
    void tearDown() {
        tracker.stop();
    }

    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    private OrderBookMessage createMockMsg(OrderBookMessage.Type type, String pair, long updateId) {
        OrderBookMessage msg = new OrderBookMessage(type, pair, updateId);
        return msg;
    }

    @Test
    @DisplayName("트래커에 Metrics 속성이 존재한다.")
    void testMetricsPropertyExists() {
        OrderBookTrackerMetrics metrics = assertInstanceOf(OrderBookTrackerMetrics.class, tracker.getMetrics());
    }

    @Test
    @DisplayName("start() 호출 시 트래커 시작 시간이 초기화된다.")
    void testStartSetsTrackerStartTime() throws Exception {
        assertEquals(0.0, tracker.getMetrics().getTrackerStartTime());

        // DataSource 모킹
        when(dataSource.getNewOrderBook(anyString())).thenReturn(mock(OrderBook.class));

        tracker.start();

        assertTrue(tracker.getMetrics().getTrackerStartTime() > 0);
    }

    @Test
    @DisplayName("Diff 라우터가 메트릭을 정상적으로 업데이트한다.")
    void testDiffRouterUpdatesMetrics() throws Exception {
        OrderBook mockBook = orderBooks.get("BTC-USDT");
        when(mockBook.getSnapshotId()).thenReturn(100L);

        tracker.start();

        // Diff 메시지 주입
        OrderBookMessage msg = createMockMsg(OrderBookMessageType.DIFF, "BTC-USDT", 150L);
        diffStream.put(msg);

        // 비동기 처리 대기
        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalDiffsProcessed());
        assertNotNull(tracker.getMetrics().getOrCreatePairMetrics("BTC-USDT"));
    }

    @Test
    @DisplayName("Diff 라우터가 스냅샷보다 오래된 diff 메시지를 무시한다.")
    void testDiffRouterTracksRejectedMessages() throws Exception {
        when(orderBooks.get("BTC-USDT").getSnapshotId()).thenReturn(200L);

        tracker.start();

        // 낮은 UID를 가진 diff 메시지 주입
        OrderBookMessage msg = createMockMsg(OrderBookMessageType.DIFF, "BTC-USDT", 150L);
        diffStream.put(msg);

        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalDiffsRejected());
    }

    @Test
    @DisplayName("등록되지 않은 페어의 diff 메시지가 들어오면 임시 큐에 저장한다.")
    void testDiffRouterTracksQueuedMessages() throws Exception {
        tracker.start();

        String unknownPair = "SOL-USDT";

        // 등록되지 않은 페어의 diff 메시지 주입
        OrderBookMessage msg = createMockMsg(OrderBookMessageType.DIFF, unknownPair, 150L);
        diffStream.put(msg);

        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalDiffsQueued());

        assertTrue(savedMessageQueues.containsKey(unknownPair));
        assertEquals(1, savedMessageQueues.get(unknownPair).size());

        OrderBookMessage savedMsg = savedMessageQueues.get(unknownPair).peek();
        assertEquals(unknownPair, savedMsg.getTradingPair());
    }

    @Test
    @DisplayName("Snapshot 라우터가 메트릭을 정상적으로 업데이트한다.")
    void testSnapshotRouterUpdatesMetrics() throws Exception {
        tracker.start();

        OrderBookMessage msg = createMockMsg(OrderBookMessageType.SNAPSHOT, "BTC-USDT", 100L);
        snapshotStream.put(msg);

        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalSnapshotsProcessed());
    }

    @Test
    @DisplayName("Trade 라우터가 메트릭을 정상적으로 업데이트하고 메시지를 처리한다.")
    void testTradeRouterUpdatesMetrics() throws Exception {
        tracker.start();

        String pair = "BTC-USDT";
        OrderBook mockBook = orderBooks.get(pair);

        OrderBookMessage msg = createMockMsg(OrderBookMessageType.TRADE, pair, 150L);
        tradeStream.put(msg);

        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalTradesProcessed());

        assertEquals(1, tracker.getMetrics().getOrCreatePairMetrics(pair).getTradesProcessed(), "페어별 Trade 처리 메트릭이 증가해야 합니다.");

        verify(mockBook, times(1)).applyTrade(msg);
    }

    @Test
    @DisplayName("등록되지 않은 페어의 Trade 메시지가 들어오면 Reject 된다.")
    void testTradeRouterTracksRejectedTrades() throws Exception {
        tracker.start();

        String unknownPair = "UNKNOWN-PAIR";

        OrderBookMessage msg = createMockMsg(OrderBookMessageType.TRADE, unknownPair, 150L);
        tradeStream.put(msg);

        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalTradesRejected(), "Rejected 메트릭이 증가해야 합니다.");
    }

    @Test
    @DisplayName("트레이딩 페어가 정상적으로 추가된다.")
    void testAddTradingPairSuccessful() throws Exception {
        when(dataSource.getNewOrderBook(anyString())).thenReturn(mock(OrderBook.class));
        when(dataSource.subscribeToTradingPair(anyString())).thenReturn(true);

        tracker.start();
        tracker.waitReady();

        String newPair = "SOL-USDT";
        boolean result = tracker.addTradingPair(newPair);

        assertTrue(result);
        assertTrue(orderBooks.containsKey(newPair));
        verify(dataSource).subscribeToTradingPair(newPair);
    }

    @Test
    @DisplayName("이미 트래킹 중인 페어 추가 시 False가 반환된다.")
    void testAddTradingPairAlreadyTracked() throws Exception {
        tracker.start();
        tracker.waitReady();

        boolean result = tracker.addTradingPair("BTC-USDT");

        assertFalse(result);
        verify(dataSource, never()).getNewOrderBook("BTC-USDT");
    }

    @Test
    @DisplayName("트레이딩 페어 삭제 시 데이터 및 메트릭이 삭제된다.")
    void testRemoveTradingPairSuccessful() throws Exception {
        when(dataSource.unsubscribeFromTradingPair(anyString())).thenReturn(true);

        tracker.start();
        tracker.waitReady();

        tracker.getMetrics().getOrCreatePairMetrics("BTC-USDT");

        boolean result = tracker.removeTradingPair("BTC-USDT");

        assertTrue(result);
        assertFalse(orderBooks.containsKey("BTC-USDT"));
    }

    @Test
    @DisplayName("구독 실패 시 페어 추가가 정상적으로 실패 처리된다.")
    void testAddTradingPairSubscriptionFails() throws Exception {
        when(dataSource.getNewOrderBook(anyString())).thenReturn(mock(OrderBook.class));
        when(dataSource.subscribeToTradingPair("SOL-USDT")).thenReturn(false);

        tracker.start();
        tracker.waitReady();

        boolean result = tracker.addTradingPair("SOL-USDT");

        assertFalse(result);
        assertFalse(orderBooks.containsKey("SOL-USDT"));
    }
}