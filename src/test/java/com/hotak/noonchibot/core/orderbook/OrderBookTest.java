package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.TradeType;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class OrderBookTest {
    @Test
    @DisplayName("빈 오더북에 스냅샷 적용")
    void applySnapshotToEmptyBook() {
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20")),
                new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("30"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        OrderBook orderBook = new OrderBook(false);

        orderBook.applySnapshot(bids, asks, 1L);

        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100");
        assertThat(orderBook.getBestAsk()).isEqualByComparingTo("101");
        assertThat(orderBook.getSnapshotId()).isEqualTo(1L);
    }


    @Test
    @DisplayName("스냅샷 적용 시 기존 데이터 초기화")
    void snapshotClearsPreviousData() {
        OrderBook orderBook = new OrderBook(false);
        // 기존 데이터 적용
        orderBook.applySnapshot(
                List.of(new OrderBookEntry(1L, new BigDecimal("50"), new BigDecimal("10"))),
                List.of(new OrderBookEntry(1L, new BigDecimal("60"), new BigDecimal("10"))),
                1L
        );
        // 새 스냅샷 적용
        orderBook.applySnapshot(
                List.of(new OrderBookEntry(2L, new BigDecimal("100"), new BigDecimal("5"))),
                List.of(new OrderBookEntry(2L, new BigDecimal("101"), new BigDecimal("5"))),
                2L
        );
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100");
        assertThat(orderBook.getBestAsk()).isEqualByComparingTo("101");
    }

    @Test
    @DisplayName("새 가격 레벨 추가")
    void addNewPriceLevel() {
        OrderBook orderBook = new OrderBook(false);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        orderBook.applySnapshot(bids, asks, 1L);

        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("98"), new BigDecimal("50"))),
                List.of(new OrderBookEntry(2L, new BigDecimal("103"), new BigDecimal("25"))),
                2L
        );
        assertThat(orderBook.getLastDiffId()).isEqualTo(2L);
        // best prices 유지 확인
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100");
        assertThat(orderBook.getBestAsk()).isEqualByComparingTo("101");
    }

    @Test
    @DisplayName("기존 가격 레벨 수량 업데이트")
    void updateExistingPriceLevel() {
        OrderBook orderBook = new OrderBook(false);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        orderBook.applySnapshot(bids, asks, 1L);

        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("100"), new BigDecimal("999"))),
                List.of(),
                2L
        );

        // getBidBook() 메서드가 있다고 가정
        OrderBookEntry updatedEntry = orderBook.getBidEntries().getFirst();
        assertThat(updatedEntry.amount()).isEqualByComparingTo("999");
    }

    @Test
    @DisplayName("수량이 0이면 가격 레벨 삭제")
    void removePriceLevelWithZeroAmount() {
        OrderBook orderBook = new OrderBook(false);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        orderBook.applySnapshot(bids, asks, 1L);

        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("100"), BigDecimal.ZERO)),
                List.of(),
                2L
        );

        assertThat(orderBook.getBidEntries()).extracting(OrderBookEntry::price).doesNotContain(new BigDecimal("100"));
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("99");
    }

    @Test
    @DisplayName("best bid 삭제 시 다음 best bid로 갱신")
    void updateBestBidAfterRemoval() {
        OrderBook orderBook = new OrderBook(false);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        orderBook.applySnapshot(bids, asks, 1L);

        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("100"), BigDecimal.ZERO)),
                List.of(),
                2L
        );
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("99");
    }

    @Test
    @DisplayName("새로운 best bid 추가")
    void addNewBestBid() {
        OrderBook orderBook = new OrderBook(false);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        orderBook.applySnapshot(bids, asks, 1L);

        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("100.5"), new BigDecimal("5"))),
                List.of(),
                2L
        );
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100.5");
    }

    @Test
    @DisplayName("새로운 best ask 추가")
    void addNewBestAsk() {
        OrderBook orderBook = new OrderBook(false);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("15"))
        );
        orderBook.applySnapshot(bids, asks, 1L);

        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("100.5"), new BigDecimal("5"))),
                List.of(),
                2L
        );
        orderBook.applyDiffs(
                List.of(),
                List.of(new OrderBookEntry(2L, new BigDecimal("100.5"), new BigDecimal("3"))),
                2L
        );

        assertThat(orderBook.getBestAsk()).isEqualByComparingTo("100.5");
    }

    @Test
    @DisplayName("오버랩 없는 경우 - 변경 없음")
    void noOverlap() {
        OrderBook orderBook = new OrderBook(true);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10"))
        );

        orderBook.applySnapshot(bids, asks, 1L);

        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100");
        assertThat(orderBook.getBestAsk()).isEqualByComparingTo("101");
    }

    @Test
    @DisplayName("동일 수량 오버랩 - 양쪽 모두 삭제")
    void equalAmountOverlap() {
        OrderBook orderBook = new OrderBook(true);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))
        );

        orderBook.applySnapshot(bids, asks, 1L);

        assertThat(orderBook.getBidEntries()).isEmpty();
        assertThat(orderBook.getAskEntries()).isEmpty();
        assertThat(orderBook.getBestBid()).isNull();
        assertThat(orderBook.getBestAsk()).isNull();
    }

    @Test
    @DisplayName("매수 수량이 더 큰 오버랩")
    void bidLargerOverlap() {
        OrderBook orderBook = new OrderBook(true);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))
        );

        orderBook.applySnapshot(bids, asks, 1L);

        // 매도 전량 체결, 매수 5 잔량
        assertThat(orderBook.getAskEntries()).isEmpty();
        assertThat(orderBook.getBidEntries().getFirst().amount()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("매도 수량이 더 큰 오버랩")
    void askLargerOverlap() {
        OrderBook orderBook = new OrderBook(true);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15"))
        );

        orderBook.applySnapshot(bids, asks, 1L);

        // 매수 전량 체결, 매도 5 잔량
        assertThat(orderBook.getBidEntries()).isEmpty();
        assertThat(orderBook.getAskEntries().getFirst().amount()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("다중 레벨 오버랩 처리")
    void multiLevelOverlap() {
        OrderBook orderBook = new OrderBook(true);
        // 매수: 100@10, 99@20
        // 매도: 98@5, 99@10
        // 매수 100 >= 매도 98 -> 오버랩
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("20"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("10"))
        );

        orderBook.applySnapshot(bids, asks, 1L);
        // 100 vs 98: bid 10, ask 5 -> ask 삭제, bid 5 잔량
        // 5@100 vs 99@10: bid 5, ask 10 -> bid 삭제, ask 5 잔량
        // 99@20 vs 99@5: bid 20, ask 5 -> ask 삭제, bid 15 잔량
        // 99@15 vs empty -> 종료
        assertThat(orderBook.getBidEntries()).hasSize(1);
        assertThat(orderBook.getBidEntries().getFirst().price()).isEqualByComparingTo("99");
        assertThat(orderBook.getBidEntries().getFirst().amount()).isEqualByComparingTo("15");
        assertThat(orderBook.getAskEntries()).isEmpty();
    }

    @Test
    @DisplayName("매수가 > 매도가인 다중 레벨")
    void bidHigherThanAskMultiLevel() {
        OrderBook orderBook = new OrderBook(true);
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("105"), new BigDecimal("5")),
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))
        );
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("95"), new BigDecimal("20"))
        );

        orderBook.applySnapshot(bids, asks, 1L);

        // 105@5 vs 95@20 -> bid 삭제, ask 15 잔량
        // 100@10 vs 95@15 -> bid 삭제, ask 5 잔량
        // empty vs 95@5 -> 종료
        assertThat(orderBook.getBidEntries()).isEmpty();
        assertThat(orderBook.getAskEntries().getFirst().price()).isEqualByComparingTo("95");
        assertThat(orderBook.getAskEntries().getFirst().amount()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("한쪽만 있는 오더북")
    void oneSidedOrderBook() {
        OrderBook orderBook = new OrderBook(true);
        orderBook.applySnapshot(
                List.of(new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))),
                List.of(),
                1L
        );
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100");
        assertThat(orderBook.getBestAsk()).isNull();
    }

    @Test
    @DisplayName("모든 항목 삭제")
    void removeAllEntries() {
        OrderBook orderBook = new OrderBook(true);
        orderBook.applySnapshot(
                List.of(new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("10"))),
                List.of(new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("5"))),
                1L
        );
        orderBook.applyDiffs(
                List.of(new OrderBookEntry(2L, new BigDecimal("100"), BigDecimal.ZERO)),
                List.of(new OrderBookEntry(2L, new BigDecimal("101"), BigDecimal.ZERO)),
                2L
        );
        assertThat(orderBook.getBestBid()).isNull();
        assertThat(orderBook.getBestAsk()).isNull();
    }

    @Test
    @DisplayName("소수점 가격 처리")
    void decimalPrices() {
        OrderBook orderBook = new OrderBook(true);
        orderBook.applySnapshot(
                List.of(
                        new OrderBookEntry(1L, new BigDecimal("100.123"), new BigDecimal("1.5")),
                        new OrderBookEntry(1L, new BigDecimal("100.122"), new BigDecimal("2.5"))
                ),
                List.of(
                        new OrderBookEntry(1L, new BigDecimal("100.124"), new BigDecimal("0.5"))
                ),
                1L
        );
        assertThat(orderBook.getBestBid()).isEqualByComparingTo("100.123");
        assertThat(orderBook.getBestAsk()).isEqualByComparingTo("100.124");
    }

    @Test
    @DisplayName("매수 시 best ask 반환")
    void returnsBestAskForBuy() {
        OrderBook orderBook = new OrderBook(false);

        // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
        // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
        );
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
        );
        orderBook.applySnapshot(bids, asks, 1L);
        assertThat(orderBook.getBestPrice(true)).isEqualByComparingTo("101");
    }

    @Test
    @DisplayName("매도 시 best bid 반환")
    void returnsBestBidForSell() {
        OrderBook orderBook = new OrderBook(false);

        // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
        // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
        List<OrderBookEntry> asks = List.of(
                new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
        );
        List<OrderBookEntry> bids = List.of(
                new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
        );
        orderBook.applySnapshot(bids, asks, 1L);
        assertThat(orderBook.getBestPrice(false)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("빈 오더북은 null 반환")
    void returnsNullForEmptyBook() {
        OrderBook emptyBook = new OrderBook(false);
        assertThat(emptyBook.getBestPrice(true)).isNull();
        assertThat(emptyBook.getBestPrice(false)).isNull();
    }

    @Nested
    @DisplayName("getImpactPriceForBaseVolume")
    class GetImpactPriceForBaseVolumeTest {

        private OrderBook orderBook;
        @BeforeEach
        void setUp() {
            OrderBook orderBook = new OrderBook(false);
            // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
            // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
            List<OrderBookEntry> asks = List.of(
                    new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                    new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                    new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
            );
            List<OrderBookEntry> bids = List.of(
                    new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                    new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                    new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
            );
            orderBook.applySnapshot(bids, asks, 1L);
        }

        @Test
        @DisplayName("첫 레벨에서 충족")
        void filledAtFirstLevel() {
            OrderBookQueryResult result = orderBook.getImpactPriceForBaseVolume(true, new BigDecimal("5"));

            assertThat(result.queryPrice()).isEqualByComparingTo("101");
            assertThat(result.queryVolume()).isEqualByComparingTo("5");
            assertThat(result.resultPrice()).isEqualByComparingTo("101");
            assertThat(result.resultVolume()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("여러 레벨에 걸쳐 충족")
        void filledAcrossMultipleLevels() {
            // 25개 매수: 101에서 10개, 102에서 15개
            OrderBookQueryResult result = orderBook.getImpactPriceForBaseVolume(true, new BigDecimal("25"));

            assertThat(result.queryPrice()).isEqualByComparingTo("101");
            assertThat(result.resultPrice()).isEqualByComparingTo("102");
            assertThat(result.resultVolume()).isEqualByComparingTo("25");
        }

        @Test
        @DisplayName("호가 부족")
        void insufficientLiquidity() {
            // 100개 매수 시도 (총 60개만 있음)
            OrderBookQueryResult result = orderBook.getImpactPriceForBaseVolume(true, new BigDecimal("100"));

            assertThat(result.queryVolume()).isEqualByComparingTo("100");
            assertThat(result.resultPrice()).isEqualByComparingTo("103");
            assertThat(result.resultVolume()).isEqualByComparingTo("60");
        }

        @Test
        @DisplayName("매도 방향")
        void sellDirection() {
            // 30개 매도: 100에서 15개, 99에서 15개
            OrderBookQueryResult result = orderBook.getImpactPriceForBaseVolume(false, new BigDecimal("30"));

            assertThat(result.queryPrice()).isEqualByComparingTo("100");
            assertThat(result.resultPrice()).isEqualByComparingTo("99");
            assertThat(result.resultVolume()).isEqualByComparingTo("30");
        }
    }

    @Nested
    @DisplayName("getVWAPForVolume")
    class GetVWAPForVolumeTest {

        private OrderBook orderBook;
        @BeforeEach
        void setUp() {
            OrderBook orderBook = new OrderBook(false);
            // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
            // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
            List<OrderBookEntry> asks = List.of(
                    new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                    new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                    new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
            );
            List<OrderBookEntry> bids = List.of(
                    new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                    new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                    new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
            );
            orderBook.applySnapshot(bids, asks, 1L);
        }

        @Test
        @DisplayName("단일 레벨 VWAP")
        void singleLevelVwap() {
            OrderBookQueryResult result = orderBook.getVWAPForVolume(true, new BigDecimal("10"));

            // 101 * 10 / 10 = 101
            assertThat(result.resultPrice()).isEqualByComparingTo("101");
            assertThat(result.resultVolume()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("여러 레벨 VWAP")
        void multiLevelVwap() {
            // 25개 매수: 101*10 + 102*15 = 1010 + 1530 = 2540
            // VWAP = 2540 / 25 = 101.6
            OrderBookQueryResult result = orderBook.getVWAPForVolume(true, new BigDecimal("25"));

            assertThat(result.resultPrice()).isEqualByComparingTo("101.6");
            assertThat(result.resultVolume()).isEqualByComparingTo("25");
        }

        @Test
        @DisplayName("전체 레벨 VWAP")
        void allLevelsVwap() {
            // 60개 매수: 101*10 + 102*20 + 103*30 = 1010 + 2040 + 3090 = 6140
            // VWAP = 6140 / 60 = 102.333...
            OrderBookQueryResult result = orderBook.getVWAPForVolume(true, new BigDecimal("60"));

            assertThat(result.resultPrice()).isEqualByComparingTo("102.33333333");
            assertThat(result.resultVolume()).isEqualByComparingTo("60");
        }

        @Test
        @DisplayName("호가 부족 시 가능한 수량만 VWAP 계산")
        void insufficientLiquidityVwap() {
            OrderBookQueryResult result = orderBook.getVWAPForVolume(true, new BigDecimal("100"));

            // 60개만 가능, VWAP = 6140 / 60
            assertThat(result.queryVolume()).isEqualByComparingTo("100");
            assertThat(result.resultVolume()).isEqualByComparingTo("60");
            assertThat(result.resultPrice()).isEqualByComparingTo("102.33333333");
        }
    }

    @Nested
    @DisplayName("getImpactPriceForQuoteVolume")
    class GetImpactPriceForQuoteVolumeTest {

        private OrderBook orderBook;
        @BeforeEach
        void setUp() {
            OrderBook orderBook = new OrderBook(false);
            // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
            // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
            List<OrderBookEntry> asks = List.of(
                    new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                    new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                    new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
            );
            List<OrderBookEntry> bids = List.of(
                    new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                    new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                    new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
            );
            orderBook.applySnapshot(bids, asks, 1L);
        }


        @Test
        @DisplayName("첫 레벨에서 충족")
        void filledAtFirstLevel() {
            // 500 USDT: 101 * 10 = 1010 > 500
            OrderBookQueryResult result = orderBook.getImpactPriceForQuoteVolume(true, new BigDecimal("500"));

            assertThat(result.resultPrice()).isEqualByComparingTo("101");
            assertThat(result.resultVolume()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("여러 레벨에 걸쳐 충족")
        void filledAcrossMultipleLevels() {
            // 2000 USDT: 101*10=1010, 102*20=2040 -> 1010+2040=3050 > 2000
            OrderBookQueryResult result = orderBook.getImpactPriceForQuoteVolume(true, new BigDecimal("2000"));

            assertThat(result.resultPrice()).isEqualByComparingTo("102");
            assertThat(result.resultVolume()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("호가 부족")
        void insufficientLiquidity() {
            // 10000 USDT 시도 (총 6140만 있음)
            OrderBookQueryResult result = orderBook.getImpactPriceForQuoteVolume(true, new BigDecimal("10000"));

            assertThat(result.queryVolume()).isEqualByComparingTo("10000");
            assertThat(result.resultPrice()).isEqualByComparingTo("103");
            assertThat(result.resultVolume()).isEqualByComparingTo("6140");
        }
    }

    @Nested
    @DisplayName("getQuoteVolumeForBaseVolume")
    class GetQuoteVolumeForBaseAmountTest {
        private OrderBook orderBook;

        @BeforeEach
        void setUp() {
            OrderBook orderBook = new OrderBook(false);
            // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
            // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
            List<OrderBookEntry> asks = List.of(
                    new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                    new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                    new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
            );
            List<OrderBookEntry> bids = List.of(
                    new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                    new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                    new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
            );
            orderBook.applySnapshot(bids, asks, 1L);
        }

        @Test
        @DisplayName("단일 레벨에서 충족")
        void singleLevel() {
            // 5 BTC 매수: 101 * 5 = 505 USDT 필요
            OrderBookQueryResult result = orderBook.getQuoteVolumeForBaseVolume(true, new BigDecimal("5"));

            assertThat(result.resultVolume()).isEqualByComparingTo("505");
        }

        @Test
        @DisplayName("여러 레벨에 걸쳐 계산")
        void multipleLevel() {
            // 25 BTC 매수: 101*10 + 102*15 = 1010 + 1530 = 2540 USDT
            OrderBookQueryResult result = orderBook.getQuoteVolumeForBaseVolume(true, new BigDecimal("25"));

            assertThat(result.resultVolume()).isEqualByComparingTo("2540");
        }

        @Test
        @DisplayName("호가 부족")
        void insufficientLiquidity() {
            // 100 BTC 시도 (60개만 있음)
            OrderBookQueryResult result = orderBook.getQuoteVolumeForBaseVolume(true, new BigDecimal("100"));

            assertThat(result.queryVolume()).isEqualByComparingTo("100");
            assertThat(result.resultVolume()).isEqualByComparingTo("6140"); // 가능한 만큼만
        }

        @Test
        @DisplayName("매도 방향")
        void sellDirection() {
            // 30 BTC 매도: 100*15 + 99*15 = 1500 + 1485 = 2985 USDT
            OrderBookQueryResult result = orderBook.getQuoteVolumeForBaseVolume(false, new BigDecimal("30"));

            assertThat(result.resultVolume()).isEqualByComparingTo("2985");
        }
    }

    @Nested
    @DisplayName("getVolumeForPrice")
    class GetVolumeForPriceTest {
        private OrderBook orderBook;

        @BeforeEach
        void setUp() {
            OrderBook orderBook = new OrderBook(false);
            // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
            // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
            List<OrderBookEntry> asks = List.of(
                    new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                    new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                    new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
            );
            List<OrderBookEntry> bids = List.of(
                    new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                    new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                    new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
            );
            orderBook.applySnapshot(bids, asks, 1L);
        }

        @Test
        @DisplayName("첫 레벨만 포함")
        void firstLevelOnly() {
            // 101 이하로 매수 가능한 수량
            OrderBookQueryResult result = orderBook.getVolumeForPrice(true, new BigDecimal("101"));

            assertThat(result.resultPrice()).isEqualByComparingTo("101");
            assertThat(result.resultVolume()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("여러 레벨 포함")
        void multipleLevels() {
            // 102 이하로 매수 가능한 수량
            OrderBookQueryResult result = orderBook.getVolumeForPrice(true, new BigDecimal("102"));

            assertThat(result.resultPrice()).isEqualByComparingTo("102");
            assertThat(result.resultVolume()).isEqualByComparingTo("30"); // 10 + 20
        }

        @Test
        @DisplayName("모든 레벨 포함")
        void allLevels() {
            // 105 이하로 매수 가능한 수량 (전체)
            OrderBookQueryResult result = orderBook.getVolumeForPrice(true, new BigDecimal("105"));

            assertThat(result.resultPrice()).isEqualByComparingTo("103");
            assertThat(result.resultVolume()).isEqualByComparingTo("60");
        }

        @Test
        @DisplayName("해당 가격 없음")
        void noPriceInRange() {
            // 100 이하로 매수 (ask는 101부터)
            OrderBookQueryResult result = orderBook.getVolumeForPrice(true, new BigDecimal("100"));

            assertThat(result.resultPrice()).isNull();
            assertThat(result.resultVolume()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("매도 방향")
        void sellDirection() {
            // 99 이상으로 매도 가능한 수량
            OrderBookQueryResult result = orderBook.getVolumeForPrice(false, new BigDecimal("99"));

            assertThat(result.resultPrice()).isEqualByComparingTo("99");
            assertThat(result.resultVolume()).isEqualByComparingTo("40"); // 15 + 25
        }
    }

    @Nested
    @DisplayName("getQuoteVolumeForPrice")
    class GetQuoteVolumeForPriceTest {
        private OrderBook orderBook;

        @BeforeEach
        void setUp() {
            OrderBook orderBook = new OrderBook(false);
            // Ask Book: 101 -> 10, 102 -> 20, 103 -> 30
            // Bid Book: 100 -> 15, 99 -> 25, 98 -> 35
            List<OrderBookEntry> asks = List.of(
                    new OrderBookEntry(1L, new BigDecimal("101"), new BigDecimal("10")),
                    new OrderBookEntry(1L, new BigDecimal("102"), new BigDecimal("20")),
                    new OrderBookEntry(1L, new BigDecimal("103"), new BigDecimal("30"))
            );
            List<OrderBookEntry> bids = List.of(
                    new OrderBookEntry(1L, new BigDecimal("100"), new BigDecimal("15")),
                    new OrderBookEntry(1L, new BigDecimal("99"), new BigDecimal("25")),
                    new OrderBookEntry(1L, new BigDecimal("98"), new BigDecimal("35"))
            );
            orderBook.applySnapshot(bids, asks, 1L);
        }

        @Test
        @DisplayName("첫 레벨만 포함")
        void firstLevelOnly() {
            // 101 이하로 매수 시 필요 금액
            OrderBookQueryResult result = orderBook.getQuoteVolumeForPrice(true, new BigDecimal("101"));

            assertThat(result.resultPrice()).isEqualByComparingTo("101");
            assertThat(result.resultVolume()).isEqualByComparingTo("1010"); // 101 * 10
        }

        @Test
        @DisplayName("여러 레벨 포함")
        void multipleLevels() {
            // 102 이하로 매수 시 필요 금액
            OrderBookQueryResult result = orderBook.getQuoteVolumeForPrice(true, new BigDecimal("102"));

            assertThat(result.resultPrice()).isEqualByComparingTo("102");
            assertThat(result.resultVolume()).isEqualByComparingTo("3050"); // 1010 + 2040
        }

        @Test
        @DisplayName("해당 가격 없음")
        void noPriceInRange() {
            OrderBookQueryResult result = orderBook.getQuoteVolumeForPrice(true, new BigDecimal("100"));

            assertThat(result.resultPrice()).isNull();
            assertThat(result.resultVolume()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("매도 방향")
        void sellDirection() {
            // 99 이상으로 매도 시 받는 금액
            OrderBookQueryResult result = orderBook.getQuoteVolumeForPrice(false, new BigDecimal("99"));

            // 100*15 + 99*25 = 1500 + 2475 = 3975
            assertThat(result.resultPrice()).isEqualByComparingTo("99");
            assertThat(result.resultVolume()).isEqualByComparingTo("3975");
        }
    }

    @Test
    @DisplayName("스냅샷 이후 diff만 적용")
    void appliesOnlyDiffsAfterSnapshot() {
        OrderBook orderBook = new OrderBook(false);

        OrderBookMessage.SnapshotMessage snapshot = new OrderBookMessage.SnapshotMessage(
                Instant.parse("2024-01-01T00:01:00Z"),
                "BTC-USDT",
                5L,
                List.of(new OrderBookEntry(5L, new BigDecimal("100"), new BigDecimal("10"))),
                List.of(new OrderBookEntry(5L, new BigDecimal("101"), new BigDecimal("10")))
        );

        List<OrderBookMessage.DiffMessage> diffs = List.of(
                // 스냅샷 이전 - 무시됨
                new OrderBookMessage.DiffMessage(
                        Instant.parse("2024-01-01T00:00:00Z"),
                        "BTC-USDT",
                        3L,
                        List.of(new OrderBookEntry(3L, new BigDecimal("99"), new BigDecimal("5"))),
                        List.of()
                ),
                // 스냅샷 이후 - 적용됨
                new OrderBookMessage.DiffMessage(
                        Instant.parse("2024-01-01T00:02:00Z"),
                        "BTC-USDT",
                        6L,
                        List.of(new OrderBookEntry(6L, new BigDecimal("100"), new BigDecimal("20"))),
                        List.of()
                ),
                new OrderBookMessage.DiffMessage(
                        Instant.parse("2024-01-01T00:03:00Z"),
                        "BTC-USDT",
                        7L,
                        List.of(),
                        List.of(new OrderBookEntry(7L, new BigDecimal("102"), new BigDecimal("15")))
                )
        );

        orderBook.restoreFromSnapshotAndDiffs(snapshot, diffs);

        // 99 bid는 적용 안됨 (스냅샷 이전)
        assertThat(orderBook.getBidEntries())
                .extracting(OrderBookEntry::price)
                .doesNotContain(new BigDecimal("99"));

        // 100 bid는 20으로 업데이트됨
        assertThat(orderBook.getBidEntries().getFirst().amount())
                .isEqualByComparingTo("20");

        // 102 ask 추가됨
        assertThat(orderBook.getAskEntries())
                .extracting(OrderBookEntry::price)
                .contains(new BigDecimal("102"));
    }

    @Test
    @DisplayName("Trade 메시지 적용 시 lastTradeTime, lastTradePrice 업데이트")
    void applyTradeUpdatesTimeAndPrice() {
        OrderBook orderBook = new OrderBook(false);

        OrderBookMessage.TradeMessage trade = new OrderBookMessage.TradeMessage(
                Instant.parse("2024-01-01T00:05:00Z"),
                "BTC-USDT",
                1L,
                new BigDecimal("50000.5"),
                new BigDecimal("0.1"),
                TradeType.BUY
        );
        orderBook.applyTrade(trade);
        assertThat(orderBook.getLastAppliedTradeTime()).isEqualTo(Instant.parse("2024-01-01T00:05:00Z"));
        assertThat(orderBook.getLastTradePrice()).isEqualByComparingTo("50000.5");
    }
}