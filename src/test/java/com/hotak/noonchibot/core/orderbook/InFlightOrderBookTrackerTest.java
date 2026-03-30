package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.TradeType;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

public class InFlightOrderBookTrackerTest {

    private OrderBookDataSource dataSource;
    private TaskScheduler scheduler;
    private AsyncTaskExecutor executor;
    private OrderBook orderBook;

    private OrderBookTracker tracker;

    @BeforeEach
    void setUp() {
        dataSource = mock(OrderBookDataSource.class);
        scheduler = mock(TaskScheduler.class);
        executor = mock(AsyncTaskExecutor.class);
        orderBook = spy(new OrderBook(false));
        tracker = new OrderBookTracker(dataSource, scheduler, executor);
        when(executor.submit(any(Runnable.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenReturn(null);
        when(dataSource.getNewOrderBook(anyString())).thenReturn(orderBook);
    }

    @Test
    @DisplayName("tradingPair 추가시 datasource에서 stream을 구독한다.")
    void subscribesToDataSourceWhenTradingPairAdded() {
        String tradingPair = "BTC-USDT";
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(new OrderBookMessageStream(tradingPair));
        tracker.addTradingPair(tradingPair);
        verify(dataSource, times(1)).subscribeOrderBookStream(tradingPair);
    }

    @Test
    @DisplayName("tradingPair를 해제하면 datasource에서 stream을 구독 해제한다.")
    void unsubscribesFromDataSourceWhenTradingPairRemoved() {
        String tradingPair = "BTC-USDT";
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(stream);
        tracker.addTradingPair(tradingPair);
        tracker.removeTradingPair(tradingPair);
        verify(dataSource, times(1)).unsubscribe(stream);
    }

    @Test
    @DisplayName("tradingPair 추가시 orderBook이 생성된다.")
    void createsOrderBookWhenTradingPairAdded() {
        String tradingPair = "BTC-USDT";
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(new OrderBookMessageStream(tradingPair));
        tracker.addTradingPair(tradingPair);
        verify(dataSource, times(1)).getNewOrderBook(tradingPair);
        assertThat(tracker.getOrderBooks()).containsEntry(tradingPair, orderBook);
    }

    @Test
    @DisplayName("tradingPair를 해제하면 orderBook이 제거된다.")
    void removesOrderBookWhenTradingPairRemoved() {
        String tradingPair = "BTC-USDT";
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(new OrderBookMessageStream(tradingPair));
        tracker.addTradingPair(tradingPair);
        tracker.removeTradingPair(tradingPair);
        assertThat(tracker.getOrderBooks()).doesNotContainEntry(tradingPair, orderBook);

    }

    @Test
    @DisplayName("stream에 diff 메시지가 전달되면 diff 메시지를 오더북에 반영한다.")
    void appliesDiffMessageToOrderBook() throws InterruptedException {
        String tradingPair = "BTC-USDT";
        initOrderBook();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(stream);
        tracker.addTradingPair(tradingPair);
        OrderBookMessage.DiffMessage diffMessage = createDiffMessage();
        stream.add(diffMessage);
        tracker.processStream(stream);
        verify(orderBook, times(1)).applyDiffs(diffMessage.getBids(), diffMessage.getAsks(), diffMessage.getUpdateId());
    }

    @Test
    @DisplayName("stream에 trade 메시지가 전달되면 trade 메시지를 오더북에 반영한다. ")
    void appliesTradeMessageToOrderBook() throws InterruptedException {
        String tradingPair = "BTC-USDT";
        initOrderBook();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(stream);
        tracker.addTradingPair(tradingPair);
        OrderBookMessage.TradeMessage tradeMessage = createTradeMessage();
        stream.add(tradeMessage);
        tracker.processStream(stream);
        verify(orderBook, times(1)).applyTrade(tradeMessage);
    }

    @Test
    @DisplayName("stream에 snapshot 메시지가 전달되면 최근 저장해둔 diff와 함께 오더북을 복구한다.")
    void restoresOrderBookFromSnapshotWithPastDiffs() throws InterruptedException {
        String tradingPair = "BTC-USDT";
        initOrderBook();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(stream);
        tracker.addTradingPair(tradingPair);

        // diff를 먼저 처리해서 pastDiffsWindow에 쌓기
        OrderBookMessage.DiffMessage diff1 = createDiffMessage();
        OrderBookMessage.DiffMessage diff2 = createDiffMessage();
        stream.add(diff1);
        stream.add(diff2);
        tracker.processStream(stream);
        tracker.processStream(stream);

        // snapshot 처리
        OrderBookMessage.SnapshotMessage snapshot = createSnapshotMessage();
        stream.add(snapshot);
        tracker.processStream(stream);

        verify(orderBook).restoreFromSnapshotAndDiffs(eq(snapshot), argThat(diffs ->
                diffs.size() == 2 && diffs.contains(diff1) && diffs.contains(diff2)
        ));

    }

    @Test
    @DisplayName("trade 메시지 수신이 지연되서 lastTradedPrice 업데이트가 지연되면 직접 데이터소스에서 가져와 업데이트한다.")
    void fetchesLastTradedPriceWhenTradeMessageStale() {
        String tradingPair = "BTC-USDT";
        initOrderBook();
        OrderBookMessageStream stream = new OrderBookMessageStream(tradingPair);
        when(dataSource.subscribeOrderBookStream(tradingPair)).thenReturn(stream);
        tracker.addTradingPair(tradingPair);
        when(orderBook.getLastAppliedTradeTime()).thenReturn(Instant.now().minus(Duration.ofMinutes(4)));
        BigDecimal fallbackPrice = new BigDecimal("51000");
        when(dataSource.getLastTradedPrices(Set.of(tradingPair))).thenReturn(Map.of(tradingPair, fallbackPrice));
        tracker.updateLastTradePrices();
        verify(dataSource).getLastTradedPrices(Set.of(tradingPair));
        assertThat(orderBook.getLastTradePrice()).isEqualByComparingTo(fallbackPrice);
    }

    private void initOrderBook() {
        OrderBookMessage.SnapshotMessage snapshotMessage = createSnapshotMessage();
        orderBook.applySnapshot(snapshotMessage.getBids(), snapshotMessage.getAsks(), snapshotMessage.getUpdateId());
    }

    private OrderBookMessage.SnapshotMessage createSnapshotMessage() {
        return new OrderBookMessage.SnapshotMessage(
                Instant.now(),
                "BTC-USDT",
                1L,
                List.of(
                        new OrderBookEntry(1L, new BigDecimal("50000"), new BigDecimal("1.0")),
                        new OrderBookEntry(1L, new BigDecimal("49999"), new BigDecimal("0.5"))
                ),
                List.of(
                        new OrderBookEntry(1L, new BigDecimal("50001"), new BigDecimal("2.0")),
                        new OrderBookEntry(1L, new BigDecimal("50002"), new BigDecimal("1.5"))
                )
        );
    }

    private OrderBookMessage.DiffMessage createDiffMessage() {
        return new OrderBookMessage.DiffMessage(
                Instant.now(),
                "BTC-USDT",
                1L,
                List.of(new OrderBookEntry(1L, new BigDecimal("50000"), new BigDecimal("1.0"))),
                List.of(new OrderBookEntry(1L, new BigDecimal("50001"), new BigDecimal("0.5")))
        );
    }

    private OrderBookMessage.TradeMessage createTradeMessage() {
        return new OrderBookMessage.TradeMessage(
                Instant.now(),
                "BTC-USDT",
                1L,
                new BigDecimal("50000"),
                new BigDecimal("0.5"),
                TradeType.BUY
        );
    }

}