package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.TestMainExecutor;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import com.hotak.noonchibot.core.datatype.TradeType;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

public class OrderBookTrackerTest {

    private OrderBookDataSource dataSource;
    private TestMainExecutor mainExecutor;
    private IoExecutor ioExecutor;
    private OrderBook orderBook;

    private OrderBookTracker tracker;

    @BeforeEach
    void setUp() {
        dataSource = mock(OrderBookDataSource.class);
        mainExecutor = new TestMainExecutor();
        ioExecutor = new VirtualThreadIoExecutor();
        orderBook = spy(new OrderBook(false));
        tracker = new OrderBookTracker(dataSource, mainExecutor, ioExecutor, "test-platform");
        when(dataSource.getNewOrderBook(anyString())).thenReturn(orderBook);
    }

    @Test
    @DisplayName("페어 추가 시 오더북이 생성되고 스트림이 구독된다")
    void addTradingPairCreatesOrderBookAndSubscribesStream() {
        String pair = "BTC-USDT";
        OrderBookMessageStream stream = new OrderBookMessageStream(pair);
        when(dataSource.subscribeOrderBookStream(pair)).thenReturn(stream);
        when(dataSource.getNewOrderBook(anyString())).thenReturn(orderBook);
        tracker.addTradingPair(pair);

        verify(dataSource).getNewOrderBook(pair);
        verify(dataSource).subscribeOrderBookStream(pair);
        assertThat(tracker.findOrderBook(pair)).isPresent();
    }

    @Test
    @DisplayName("이미 존재하는 페어를 추가하면 무시된다")
    void addTradingPairIgnoresDuplicate() {
        String pair = "BTC-USDT";
        OrderBookMessageStream stream = new OrderBookMessageStream(pair);
        when(dataSource.subscribeOrderBookStream(pair)).thenReturn(stream);
        when(dataSource.getNewOrderBook(anyString())).thenReturn(orderBook);

        tracker.addTradingPair(pair);
        tracker.addTradingPair(pair);
        verify(dataSource, times(1)).getNewOrderBook(pair);
    }

    @Test
    @DisplayName("페어 제거 시 오더북이 삭제되고 스트림이 구독 해제된다")
    void removeTradingPairRemovesOrderBookAndUnsubscribes() {
        String pair = "BTC-USDT";
        OrderBookMessageStream stream = new OrderBookMessageStream(pair);
        when(dataSource.subscribeOrderBookStream(pair)).thenReturn(stream);

        tracker.addTradingPair(pair);
        tracker.removeTradingPair(pair);

        assertThat(tracker.findOrderBook(pair)).isEmpty();
        verify(dataSource).unsubscribe(stream);
    }


    @Test
    @DisplayName("트래킹 중이 아닌 페어를 제거하면 무시된다")
    void removeTradingPairIgnoresUnknownPair() {
        tracker.removeTradingPair("ETH-USDT");
        verify(dataSource, never()).unsubscribe(any());
    }

    @Nested
    @DisplayName("오더북 메시지 처리")
    class MessageProcessing {

        private OrderBookMessageStream stream;

        @BeforeEach
        void setUp() {
            String pair = "BTC-USDT";
            stream = new OrderBookMessageStream(pair);
            when(dataSource.subscribeOrderBookStream(pair)).thenReturn(stream);
            tracker.addTradingPair(pair);
            when(orderBook.getSnapshotId()).thenReturn(1L);
        }

        @Test
        @DisplayName("diff 메시지가 오더북에 반영된다")
        void diffMessageAppliedToOrderBook() {
            OrderBookMessage.DiffMessage diff = createDiffMessage(2L);
            tracker.processMessage(diff);
            verify(orderBook).applyDiffs(diff.getBids(), diff.getAsks(), diff.getUpdateId());
        }

        @Test
        @DisplayName("스냅샷보다 오래된 diff는 무시된다")
        void staleDiffIgnored() {
            OrderBookMessage.DiffMessage staleDiff = createDiffMessage(0L);
            tracker.processMessage(staleDiff);
            verify(orderBook, never()).applyDiffs(any(), any(), anyLong());
        }

        @Test
        @DisplayName("trade 메시지가 오더북에 반영된다")
        void tradeMessageAppliedToOrderBook() {
            OrderBookMessage.TradeMessage trade = createTradeMessage(0L);
            tracker.processMessage(trade);
            verify(orderBook).applyTrade(trade);
        }

        @Test
        @DisplayName("스냅샷 메시지가 최근 diff 윈도우와 함께 복구된다")
        void snapshotMessageRestoresWithPastDiffs() {
            OrderBookMessage.DiffMessage diff1 = createDiffMessage(2L);
            OrderBookMessage.DiffMessage diff2 = createDiffMessage(3L);
            tracker.processMessage(diff1);
            tracker.processMessage(diff2);
            OrderBookMessage.SnapshotMessage snapshot = createSnapshotMessage(1L);
            tracker.processMessage(snapshot);
            verify(orderBook).restoreFromSnapshotAndDiffs(eq(snapshot), argThat(diffs ->
                    diffs.size() == 2 && diffs.contains(diff1) && diffs.contains(diff2)
            ));
        }

        @Test
        @DisplayName("diff 윈도우가 최대 크기를 초과하면 오래된 것부터 제거된다")
        void pastDiffsWindow_evictsOldEntries() {
            for (int i = 0; i < 35; i++) {
                tracker.processMessage(createDiffMessage(i + 2L));
            }
            Deque<OrderBookMessage.DiffMessage> window = tracker.getPastDiffsWindow("BTC-USDT");
            assertThat(window).hasSize(30);
            assertThat(window.getFirst().getUpdateId()).isEqualTo(7L);  // 2+5, 앞의 5개 제거됨
            assertThat(window.getLast().getUpdateId()).isEqualTo(36L);
        }
    }

    private OrderBookMessage.SnapshotMessage createSnapshotMessage(Long updateId) {
        return new OrderBookMessage.SnapshotMessage(
                Instant.now(),
                "BTC-USDT",
                updateId,
                List.of(
                        new OrderBookEntry(updateId, new BigDecimal("50000"), new BigDecimal("1.0")),
                        new OrderBookEntry(updateId, new BigDecimal("49999"), new BigDecimal("0.5"))
                ),
                List.of(
                        new OrderBookEntry(updateId, new BigDecimal("50001"), new BigDecimal("2.0")),
                        new OrderBookEntry(updateId, new BigDecimal("50002"), new BigDecimal("1.5"))
                )
        );
    }

    private OrderBookMessage.DiffMessage createDiffMessage(Long updateId) {
        return new OrderBookMessage.DiffMessage(
                Instant.now(),
                "BTC-USDT",
                updateId,
                List.of(new OrderBookEntry(updateId, new BigDecimal("50000"), new BigDecimal("1.0"))),
                List.of(new OrderBookEntry(updateId, new BigDecimal("50001"), new BigDecimal("0.5")))
        );
    }

    private OrderBookMessage.TradeMessage createTradeMessage(Long tradeId) {
        return new OrderBookMessage.TradeMessage(
                Instant.now(),
                "BTC-USDT",
                tradeId,
                new BigDecimal("50000"),
                new BigDecimal("0.5"),
                TradeType.BUY
        );
    }

}