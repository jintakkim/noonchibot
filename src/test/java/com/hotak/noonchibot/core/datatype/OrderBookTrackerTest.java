package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;

@ExtendWith(MockitoExtension.class)
public class OrderBookTrackerTest {

    @Mock
    private OrderBookTrackerDataSource dataSource;

    @Mock
    private TaskScheduler scheduler;

    @Mock
    private AsyncTaskExecutor executor;

    private OrderBookTracker tracker;
    private List<String> tradingPairs;

    private Map<String, OrderBook> orderBooks;
    private Map<String, Deque<OrderBookMessage>> savedMessageQueues;
    private BlockingQueue<OrderBookMessage.DiffMessage> diffQueue;
    private BlockingQueue<OrderBookMessage.SnapshotMessage> snapshotQueue;
    private BlockingQueue<OrderBookMessage.TradeMessage> tradeQueue;

    @BeforeEach
    void setUp() throws Exception {
        tradingPairs = new ArrayList<>(List.of("BTC-USDT", "ETH-USDT"));

        diffQueue = new LinkedBlockingQueue<>();
        snapshotQueue = new LinkedBlockingQueue<>();
        tradeQueue = new LinkedBlockingQueue<>();

        lenient().when(dataSource.getNewOrderBook(anyString())).thenReturn(mock(OrderBook.class));
        lenient().when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            new Thread(task).start();
            return mock(Future.class);
        });
        lenient().when(scheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

        tracker = new OrderBookTracker(dataSource, tradingPairs, "test-domain", scheduler, executor, diffQueue, snapshotQueue, tradeQueue);

        orderBooks = getPrivateField(tracker, "orderBooks");
        savedMessageQueues = getPrivateField(tracker, "savedMessageQueues");

        for (String pair : tradingPairs) {
            orderBooks.put(pair, mock(OrderBook.class));
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

    @Test
    @DisplayName("트래커에 Metrics 속성이 존재한다.")
    void testMetricsPropertyExists() {
        assertInstanceOf(OrderBookTrackerMetrics.class, tracker.getMetrics());
    }

    @Test
    @DisplayName("start() 호출 시 트래커 시작 시간이 초기화된다.")
    void testStartSetsTrackerStartTime() {
        when(dataSource.getNewOrderBook(anyString())).thenReturn(mock(OrderBook.class));

        Instant before = Instant.now();
        tracker.start();

        assertNotNull(tracker.getMetrics().getTrackerStartTime());
        assertFalse(tracker.getMetrics().getTrackerStartTime().isBefore(before));
    }

    @Test
    @DisplayName("Diff 라우터가 메트릭을 정상적으로 업데이트한다.")
    void testDiffRouterUpdatesMetrics() throws Exception {
        tracker.start();

        OrderBook mockBook = orderBooks.get("BTC-USDT");
        lenient().when(mockBook.getSnapshotId()).thenReturn(100L);

        diffQueue.put(createDiffMsg("BTC-USDT", 150L));
        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalDiffsProcessed());
    }

    @Test
    @DisplayName("Diff 라우터가 스냅샷보다 오래된 diff 메시지를 무시한다.")
    void testDiffRouterTracksRejectedMessages() throws Exception {
        tracker.start();

        OrderBook mockBook = orderBooks.get("BTC-USDT");
        when(mockBook.getSnapshotId()).thenReturn(200L);

        diffQueue.put(createDiffMsg("BTC-USDT", 150L));
        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalDiffsRejected());
    }

    @Test
    @DisplayName("등록되지 않은 페어의 diff 메시지가 들어오면 임시 큐에 저장한다.")
    void testDiffRouterTracksQueuedMessages() throws Exception {
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            new Thread(task).start();
            return mock(Future.class);
        });

        tracker.start();

        String unknownPair = "SOL-USDT";
        diffQueue.put(createDiffMsg(unknownPair, 150L));
        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalDiffsQueued());
        assertTrue(savedMessageQueues.containsKey(unknownPair));
        assertEquals(1, savedMessageQueues.get(unknownPair).size());
    }

    @Test
    @DisplayName("Snapshot 라우터가 메트릭을 정상적으로 업데이트한다.")
    void testSnapshotRouterUpdatesMetrics() throws Exception {
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            new Thread(task).start();
            return mock(Future.class);
        });

        tracker.start();

        snapshotQueue.put(createSnapshotMsg("BTC-USDT", 100L));
        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalSnapshotsProcessed());
    }

    @Test
    @DisplayName("등록되지 않은 페어의 Trade 메시지가 들어오면 Reject 된다.")
    void testTradeRouterTracksRejectedTrades() throws Exception {
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            new Thread(task).start();
            return mock(Future.class);
        });

        tracker.start();

        tradeQueue.put(createTradeMsg("unknownPair"));
        Thread.sleep(200);

        assertEquals(1, tracker.getMetrics().getTotalTradesRejected());
    }

    @Test
    @DisplayName("트레이딩 페어가 정상적으로 추가된다.")
    void testAddTradingPairSuccessful() {
        when(dataSource.getNewOrderBook(anyString())).thenReturn(mock(OrderBook.class));

        tracker.start();
        tracker.waitReady();

        String newPair = "SOL-USDT";
        tracker.addTradingPair(newPair);

        assertTrue(orderBooks.containsKey(newPair));
        verify(dataSource).subscribeToTradingPair(newPair);
    }

    @Test
    @DisplayName("이미 트래킹 중인 페어 추가 시 무시된다.")
    void testAddTradingPairAlreadyTracked() {
        tracker.start();
        tracker.waitReady();

        int beforeSize = orderBooks.size();
        tracker.addTradingPair("BTC-USDT");

        assertEquals(beforeSize, orderBooks.size());
    }

    @Test
    @DisplayName("트레이딩 페어 삭제 시 데이터 및 메트릭이 삭제된다.")
    void testRemoveTradingPairSuccessful() {
        tracker.start();
        tracker.waitReady();

        tracker.getMetrics().getOrCreatePairMetrics("BTC-USDT");
        tracker.removeTradingPair("BTC-USDT");

        assertFalse(orderBooks.containsKey("BTC-USDT"));
        verify(dataSource).unsubscribeFromTradingPair("BTC-USDT");
    }

    private OrderBookMessage.DiffMessage createDiffMsg(String pair, long updateId) {
        return new OrderBookMessage.DiffMessage(Instant.now(), pair, updateId, List.of(), List.of());
    }

    private OrderBookMessage.SnapshotMessage createSnapshotMsg(String pair, long updateId) {
        return new OrderBookMessage.SnapshotMessage(Instant.now(), pair, updateId, List.of(), List.of());
    }

    private OrderBookMessage.TradeMessage createTradeMsg(String pair) {
        return new OrderBookMessage.TradeMessage(Instant.now(), pair, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}