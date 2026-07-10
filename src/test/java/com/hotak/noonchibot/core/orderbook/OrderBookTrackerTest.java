package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookTrackerTest {
    private static final String BTC_PAIR = "BTC-USDT";
    private static final String ETH_PAIR = "ETH-USDT";
    private static final Instant EVENT_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private OrderBookTracker tracker;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        tracker = new OrderBookTracker(
                eventSubscriber,
                eventPublisher,
                false,
                Set.of(BTC_PAIR)
        );
    }

    @Nested
    @DisplayName("라이프사이클")
    class LifecycleTest {
        @Test
        @DisplayName("onStart 시 이벤트를 구독하고 configured pair 트래킹을 요청한다")
        void onStartSubscribesAndRequestsTracking() {
            tracker.onStart();

            assertThat(eventSubscriber.isSubscribed(OrderBookEvent.DiffReceived.class)).isTrue();
            assertThat(eventSubscriber.isSubscribed(OrderBookEvent.TradeReceived.class)).isTrue();
            assertThat(eventSubscriber.isSubscribed(OrderBookEvent.SnapshotReceived.class)).isTrue();
            assertThat(tracker.phase()).isEqualTo(Phases.ORDER_BOOK_TRACKER_SETUP);
            assertThat(tracker.findOrderBook(BTC_PAIR)).isPresent();
            assertThat(eventPublisher.only(OrderBookEvent.TrackingBatchRequested.class).tradingPairs())
                    .containsExactly(BTC_PAIR);
        }

        @Test
        @DisplayName("onShutdown 시 구독을 해제한다")
        void onShutdownClosesSubscriptions() {
            tracker.onStart();

            tracker.onShutdown();

            assertThat(eventSubscriber.count()).isZero();
        }
    }

    @Nested
    @DisplayName("트래킹 관리")
    class TrackingTest {
        @Test
        @DisplayName("새 tradingPair를 추가하면 OrderBook을 만들고 TrackingRequested를 발행한다")
        void addTradingPairCreatesOrderBookAndPublishesEvent() {
            tracker.addTradingPair(ETH_PAIR);

            assertThat(tracker.findOrderBook(ETH_PAIR)).isPresent();
            assertThat(eventPublisher.only(OrderBookEvent.TrackingRequested.class).tradingPair()).isEqualTo(ETH_PAIR);
        }

        @Test
        @DisplayName("이미 트래킹 중인 tradingPair를 추가하면 무시한다")
        void addTradingPairIgnoresDuplicate() {
            tracker.addTradingPair(BTC_PAIR);
            eventPublisher.clear();

            tracker.addTradingPair(BTC_PAIR);

            assertThat(tracker.getOrderBooks()).hasSize(1);
            assertThat(eventPublisher.totalCount()).isZero();
        }
    }

    @Nested
    @DisplayName("스냅샷과 diff 처리")
    class SnapshotAndDiffTest {
        @BeforeEach
        void trackPair() {
            tracker.addTradingPair(BTC_PAIR);
            eventPublisher.clear();
        }

        @Test
        @DisplayName("스냅샷 적용 전 diff는 버퍼링되고 스냅샷 수신 시 재적용된다")
        void buffersDiffBeforeSnapshotAndRestoresWithSnapshot() {
            tracker.processDiff(diff(101, bid(101, "50010", "2.0"), ask(101, "50100", "0")));

            OrderBook beforeSnapshot = tracker.findOrderBook(BTC_PAIR).orElseThrow();
            assertThat(beforeSnapshot.getSnapshotId()).isNull();
            assertThat(beforeSnapshot.getBestBid()).isNull();

            tracker.processSnapshot(snapshot(100));

            OrderBook book = tracker.findOrderBook(BTC_PAIR).orElseThrow();
            assertThat(book.getSnapshotId()).isEqualTo(100);
            assertThat(book.getLastDiffId()).isEqualTo(101);
            assertThat(book.getBestBid()).isEqualByComparingTo("50010");
            assertThat(book.getBestAsk()).isEqualByComparingTo("50200");
        }

        @Test
        @DisplayName("스냅샷 이후 최신 diff만 적용하고 오래된 diff는 무시한다")
        void appliesOnlyNewerDiffAfterSnapshot() {
            tracker.processSnapshot(snapshot(100));
            tracker.processDiff(diff(100, bid(100, "60000", "1.0"), ask(100, "60001", "1.0")));

            OrderBook book = tracker.findOrderBook(BTC_PAIR).orElseThrow();
            assertThat(book.getLastDiffId()).isNull();
            assertThat(book.getBestBid()).isEqualByComparingTo("50000");
            assertThat(book.getBestAsk()).isEqualByComparingTo("50100");

            tracker.processDiff(diff(101, bid(101, "50020", "1.0"), ask(101, "50100", "0")));
            assertThat(book.getLastDiffId()).isEqualTo(101);
            assertThat(book.getBestBid()).isEqualByComparingTo("50020");
            assertThat(book.getBestAsk()).isEqualByComparingTo("50200");

            tracker.processDiff(diff(101, bid(101, "70000", "1.0"), ask(101, "70001", "1.0")));
            assertThat(book.getBestBid()).isEqualByComparingTo("50020");
        }

        @Test
        @DisplayName("diff window는 최대 50개만 보관해 스냅샷 복구에 사용한다")
        void evictsOldDiffsFromPastWindow() {
            tracker.processDiff(diff(1, bid(1, "99999", "1.0"), ask(1, "100000", "1.0")));
            for (int updateId = 2; updateId <= 60; updateId++) {
                tracker.processDiff(diff(
                        updateId,
                        bid(updateId, String.valueOf(50000 + updateId), "1.0"),
                        ask(updateId, String.valueOf(60000 + updateId), "1.0")
                ));
            }

            tracker.processSnapshot(snapshot(0));

            OrderBook book = tracker.findOrderBook(BTC_PAIR).orElseThrow();
            assertThat(book.getLastDiffId()).isEqualTo(60);
            assertThat(book.getBestBid()).isEqualByComparingTo("50060");
        }

        @Test
        @DisplayName("트래킹 중이 아닌 pair의 snapshot/diff는 무시한다")
        void ignoresUnknownPairEvents() {
            tracker.processDiff(new OrderBookEvent.DiffReceived(
                    ETH_PAIR,
                    1,
                    List.of(bid(1, "1", "1")),
                    List.of(ask(1, "2", "1")),
                    EVENT_TIME
            ));
            tracker.processSnapshot(new OrderBookEvent.SnapshotReceived(
                    ETH_PAIR,
                    1,
                    List.of(bid(1, "1", "1")),
                    List.of(ask(1, "2", "1")),
                    EVENT_TIME
            ));

            assertThat(tracker.findOrderBook(ETH_PAIR)).isEmpty();
        }
    }

    @Nested
    @DisplayName("trade 처리")
    class TradeTest {
        @BeforeEach
        void trackPair() {
            tracker.addTradingPair(BTC_PAIR);
        }

        @Test
        @DisplayName("trade 이벤트는 last trade price/time을 갱신한다")
        void appliesTrade() {
            tracker.processTrade(new OrderBookEvent.TradeReceived(
                    BTC_PAIR,
                    1,
                    new BigDecimal("50050"),
                    new BigDecimal("0.5"),
                    TradeType.BUY,
                    EVENT_TIME
            ));

            OrderBook book = tracker.findOrderBook(BTC_PAIR).orElseThrow();
            assertThat(book.getLastTradePrice()).isEqualByComparingTo("50050");
        }

        @Test
        @DisplayName("트래킹 중이 아닌 pair의 trade는 무시한다")
        void ignoresUnknownPairTrade() {
            tracker.processTrade(new OrderBookEvent.TradeReceived(
                    ETH_PAIR,
                    1,
                    new BigDecimal("3000"),
                    new BigDecimal("1.0"),
                    TradeType.BUY,
                    EVENT_TIME
            ));

            assertThat(tracker.findOrderBook(ETH_PAIR)).isEmpty();
        }
    }

    private OrderBookEvent.SnapshotReceived snapshot(long updateId) {
        return new OrderBookEvent.SnapshotReceived(
                BTC_PAIR,
                updateId,
                List.of(
                        bid(updateId, "50000", "1.0"),
                        bid(updateId, "49900", "0.5")
                ),
                List.of(
                        ask(updateId, "50100", "1.5"),
                        ask(updateId, "50200", "2.0")
                ),
                EVENT_TIME
        );
    }

    private OrderBookEvent.DiffReceived diff(long updateId, OrderBookEntry bid, OrderBookEntry ask) {
        return new OrderBookEvent.DiffReceived(
                BTC_PAIR,
                updateId,
                List.of(bid),
                List.of(ask),
                EVENT_TIME
        );
    }

    private OrderBookEntry bid(long updateId, String price, String amount) {
        return entry(updateId, price, amount);
    }

    private OrderBookEntry ask(long updateId, String price, String amount) {
        return entry(updateId, price, amount);
    }

    private OrderBookEntry entry(long updateId, String price, String amount) {
        return new OrderBookEntry(updateId, new BigDecimal(price), new BigDecimal(amount));
    }
}
