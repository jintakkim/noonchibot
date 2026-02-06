package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.*;

public class AbstractOrderBook {
    private Long lastDiffUid;
    private Long snapshotUid;
    private boolean dex;
    private final NavigableMap<BigDecimal, OrderBookEntry> bidBook = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, OrderBookEntry> askBook = new TreeMap<>();
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal lastTradePrice;
    /**
     * 변경분 적용
     * @param bids 변경된 매수 주문 목록
     * @param asks 변경된 매도 주문 목록
     * @param updateId 업데이트 ID
     */
    public void applyDiffs(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId) {
        this.lastDiffUid = updateId;
        for (OrderBookEntry entry : bids) {
            updateBook(bidBook, entry);
        }
        for (OrderBookEntry entry : asks) {
            updateBook(askBook, entry);
        }
        refreshBestPrices();
    }

    /**
     * 스냅샷 적용
     * @param bids 전체 매수 주문 목록
     * @param asks 전체 매도 주문 목록
     * @param updateId 업데이트 ID
     */
    public void applySnapshot(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId) {
        this.bidBook.clear();
        this.askBook.clear();

        for (OrderBookEntry bid : bids) {
            this.bidBook.put(bid.price(), bid);
        }
        for (OrderBookEntry ask : asks) {
            this.askBook.put(ask.price(), ask);
        }

        // DEX 모드일 경우 겹치는 구간 정리
        if (this.dex) {
            truncateOverlapEntries(this.bidBook, this.askBook);
        }
        refreshBestPrices();
        this.snapshotUid = updateId;
    }

    private void updateBook(Map<BigDecimal, OrderBookEntry> book, OrderBookEntry entry) {
        if (entry.amount().compareTo(BigDecimal.ZERO) == 0) {
            book.remove(entry.price());
        } else {
            book.put(entry.price(), entry);
        }
    }

    private void refreshBestPrices() {
        if (!bidBook.isEmpty()) {
            this.bestBid = bidBook.firstKey();
        } else {
            this.bestBid = null;
        }

        if (!askBook.isEmpty()) {
            this.bestAsk = askBook.firstKey();
        } else {
            this.bestAsk = null;
        }
    }

    /**
     * 오더북의 겹치는 구간을 정리한다.
     * 매수 가격 >= 매도 가격 인 경우, 수량을 서로 상쇄 시킨다.
     * 일부 dex의 경우 채결 로직이 나중에 처리될 수 있기 때문에 직접 겹치는 구간을 정리해야한다.
     */
    private void truncateOverlapEntries(NavigableMap<BigDecimal, OrderBookEntry> bidBook, NavigableMap<BigDecimal, OrderBookEntry> askBook) {
        // 어느 한쪽이라도 비어있으면 즉시 종료
        while (!bidBook.isEmpty() && !askBook.isEmpty()) {
            BigDecimal bestBidPrice = bidBook.firstKey();
            BigDecimal bestAskPrice = askBook.firstKey();

            // (매수가격 < 매도가격)
            if (bestBidPrice.compareTo(bestAskPrice) < 0) {
                break;
            }
            OrderBookEntry bestBid = bidBook.get(bestBidPrice);
            OrderBookEntry bestAsk = askBook.get(bestAskPrice);

            BigDecimal bidAmount = bestBid.amount();
            BigDecimal askAmount = bestAsk.amount();
            int compareResult = bidAmount.compareTo(askAmount);

            if (compareResult == 0) {
                // 수량이 같으면 완전 체결(삭제)
                bidBook.pollFirstEntry();
                askBook.pollFirstEntry();
            }
            else if (compareResult < 0) {
                // 매도 잔량이 더 많음 -> 매수는 전부 삭제, 매도는 잔량 업데이트
                bidBook.pollFirstEntry(); // 매수 삭제
                // 매도 잔량 차감
                BigDecimal remainAsk = askAmount.subtract(bidAmount);
                OrderBookEntry updatedAsk = new OrderBookEntry(bestAsk.updateId(), remainAsk, bestAskPrice);
                askBook.put(bestAskPrice, updatedAsk);
            }
            else {
                // 매수 잔량이 더 많음 -> 매도는 전부 삭제, 매수는 잔량 업데이트
                askBook.pollFirstEntry(); // 매도 삭제
                // 매수 잔량 차감
                BigDecimal remainBid = bidAmount.subtract(askAmount);
                OrderBookEntry updatedBid = new OrderBookEntry(bestBid.updateId(), remainBid, bestBidPrice);
                bidBook.put(bestBidPrice, updatedBid);
            }
        }
    }

    /**
     * 스냅샷과 과거 Diff 데이터를 사용하여 오더북을 복구
     * @param snapshot 스냅샷 메시지
     * @param diffs 버퍼에 저장해둔 최근 Diff 메시지 리스트
     */
    public void restoreFromSnapshotAndDiffs(OrderBookMessage.SnapshotMessage snapshot, List<OrderBookMessage.DiffMessage> diffs) {
        this.applySnapshot(snapshot.getBids(), snapshot.getAsks(), snapshot.getUpdateId());
        // (스냅샷 시점 이후의 데이터만 재적용)
        diffs.stream()
                .filter(diff -> diff.getUpdateId() > snapshot.getUpdateId())
                .forEach(diff -> this.applyDiffs(diff.getBids(), diff.getAsks(), diff.getUpdateId()));
    }
}
