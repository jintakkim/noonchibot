package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.datatype.OrderBookMessage;
import com.hotak.noonchibot.core.datatype.OrderBookMessageType;
import com.hotak.noonchibot.core.orderbook.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderBookTrackerTest {

    private OrderBookTracker tracker;
    private OrderBookTrackerDataSource dataSource;
    private final String tradingPair = "BTC-USDT";

    @BeforeEach
    void setUp() {
        // 1. 데이터 소스 Mock 생성
        dataSource = Mockito.mock(OrderBookTrackerDataSource.class);

        // 2. 초기화 시 반환할 실제 오더북 객체 설정 (로직 확인을 위해 Mock이 아닌 실제 객체 사용 권장)
        OrderBook realOrderBook = new OrderBook(tradingPair) {
            @Override
            public List<OrderBookEntry> getBidEntries() {
                return List.of();
            }

            @Override
            public List<OrderBookEntry> getAskEntries() {
                return List.of();
            }

            @Override
            public Long getSnapshotId() {
                return 0L;
            }

            @Override
            public Long getLastDiffId() {
                return 0L;
            }

            @Override
            public BigDecimal getBestBid() {
                return null;
            }

            @Override
            public BigDecimal getBestAsk() {
                return null;
            }

            @Override
            public BigDecimal getLastTradePrice() {
                return null;
            }

            @Override
            public void applySnapshot(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId) {

            }

            @Override
            public void applyDiffs(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId) {

            }

            @Override
            public BigDecimal getBestPrice(boolean isBuy) {
                return null;
            }

            @Override
            public void restoreFromSnapshotAndDiffs(com.hotak.noonchibot.core.orderbook.OrderBookMessage.SnapshotMessage snapshot, List<com.hotak.noonchibot.core.orderbook.OrderBookMessage.DiffMessage> diffs) {

            }

            @Override
            public OrderBookQueryResult getImpactPriceForBaseVolume(boolean isBuy, BigDecimal volume) {
                return null;
            }

            @Override
            public OrderBookQueryResult getVWAPForVolume(boolean isBuy, BigDecimal volume) {
                return null;
            }

            @Override
            public OrderBookQueryResult getImpactPriceForQuoteVolume(boolean isBuy, BigDecimal quoteVolume) {
                return null;
            }

            @Override
            public OrderBookQueryResult getQuoteVolumeForBaseVolume(boolean isBuy, BigDecimal baseVolume) {
                return null;
            }

            @Override
            public OrderBookQueryResult getVolumeForPrice(boolean isBuy, BigDecimal price) {
                return null;
            }

            @Override
            public OrderBookQueryResult getQuoteVolumeForPrice(boolean isBuy, BigDecimal price) {
                return null;
            }
        };
        when(dataSource.getNewOrderBook(anyString())).thenReturn(realOrderBook);

        // 3. 트래커 인스턴스 생성
        tracker = new OrderBookTracker(dataSource, List.of(tradingPair));
    }

    @AfterEach
    void tearDown() {
        tracker.stop();
    }

    @Test
    @DisplayName("트래커가 시작되면 각 페어에 대한 오더북이 초기화되고 스레드 태스크가 생성되어야 한다")
    void testInitialization() {
        tracker.start();

        // 초기화 루프가 돌 때까지 잠시 대기
        await().atMost(2, TimeUnit.SECONDS).until(() -> {
            try {
                return getOrderBook(tradingPair) != null;
            } catch (Exception e) {
                return false;
            }
        });

        assertNotNull(tracker.getMetrics());
        assertTrue(tracker.getMetrics().getTrackerStartTime() > 0);
        verify(dataSource, times(1)).getNewOrderBook(tradingPair);
    }

    @Test
    @DisplayName("Diff 메시지가 diffStream에 들어오면 라우터를 거쳐 오더북에 반영되어야 한다")
    void testDiffMessageProcessing() throws Exception {
        tracker.start();

        // 초기화 대기
        await().atMost(2, TimeUnit.SECONDS).until(() -> tracker.getMetrics().getOrCreatePairMetrics(tradingPair) != null);

        // 1. 테스트용 Diff 메시지 생성
        OrderBookMessage diffMsg = new OrderBookMessage(
                OrderBookMessageType.DIFF,
                new HashMap<>(),
                System.currentTimeMillis() / 1000.0
        );

        // ⭐ 수정: 메서드 대입(=)이 아니라 Setter()를 사용해야 합니다.
        diffMsg.setTradingPair(tradingPair);
        diffMsg.setUpdateId(1000L);
        diffMsg.setBids(List.of(new double[]{50000.0, 1.0}));

        // 2. 내부 diffStream 큐에 메시지 강제 주입
        injectToQueue("diffStream", diffMsg);

        // 3. ⭐ 수정: 람다 식은 반드시 boolean(true/false)을 반환해야 합니다 (>= 1 추가)
        await().atMost(3, TimeUnit.SECONDS).until(() -> tracker.getMetrics().getTotalDiffsProcessed() >= 1);

        OrderBook book = getOrderBook(tradingPair);
        assertEquals(50000.0, book.getBestBid(), "오더북에 매수 호가가 반영되어야 함");
    }

    @Test
    @DisplayName("오더북 Snapshot ID보다 낮은 Update ID를 가진 Diff는 거절(Reject)되어야 한다")
    void testDiffRejectionLogic() throws Exception {
        tracker.start();
        await().atMost(2, TimeUnit.SECONDS).until(() -> getOrderBook(tradingPair) != null);

        // 1. ⭐ OrderBook 클래스에 getSnapshotId/setSnapshotId가 없다면 필드명을 확인하세요.
        // 보통 Hummingbot 기반이면 snapshotUid 또는 lastUpdateId일 수 있습니다.
        OrderBook book = getOrderBook(tradingPair);
        // 만약 setSnapshotId가 없다면 오더북 클래스를 확인하여 적절한 Setter를 쓰세요.
        book.setSnapshotId(2000L);

        // 2. 낮은 ID(1500)를 가진 Diff 메시지 투입
        OrderBookMessage oldMsg = new OrderBookMessage(
                OrderBookMessageType.DIFF,
                new HashMap<>(),
                System.currentTimeMillis() / 1000.0
        );
        // ⭐ 필드 직접 접근이 아니라 Setter 사용
        oldMsg.setTradingPair(tradingPair);
        oldMsg.setUpdateId(1500L);

        injectToQueue("diffStream", oldMsg);

        // 3. 검증: boolean 반환하도록 수정
        await().atMost(3, TimeUnit.SECONDS).until(() -> tracker.getMetrics().getTotalDiffsRejected() >= 1);
        assertEquals(1, tracker.getMetrics().getOrCreatePairMetrics(tradingPair).getDiffsRejected());
    }

    @Test
    @DisplayName("Trade 메시지가 들어오면 tradeStream 라우터를 통해 오더북의 applyTrade가 호출되어야 한다")
    void testTradeMessageProcessing() throws Exception {
        tracker.start();
        await().atMost(2, TimeUnit.SECONDS).until(() -> getOrderBook(tradingPair) != null);

        OrderBookMessage tradeMsg = new OrderBookMessage(
                OrderBookMessageType.TRADE,
                new HashMap<>(),
                System.currentTimeMillis() / 1000.0
        );
        tradeMsg.setTradingPair(tradingPair);
        // Trade 메시지는 보통 content 맵 안에 price와 amount를 넣거나 별도 필드가 있습니다.
        // 클래스 구조에 맞게 수정하세요.
        tradeMsg.setPrice(50500.0);
        tradeMsg.setAmount(0.5);

        injectToQueue("tradeStream", tradeMsg);

        await().atMost(3, TimeUnit.SECONDS).until(() -> tracker.getMetrics().getTotalTradesProcessed() >= 1);

        assertEquals(50500.0, getOrderBook(tradingPair).getLastTradePrice());
    }