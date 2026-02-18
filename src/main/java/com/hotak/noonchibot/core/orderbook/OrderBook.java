package com.hotak.noonchibot.core.orderbook;

import java.util.List;

public interface OrderBook extends ReadOnlyOrderBook {
    /**
     * 스냅샷 적용
     * @param bids 전체 매수 주문 목록
     * @param asks 전체 매도 주문 목록
     * @param updateId 업데이트 ID
     */
    void applySnapshot(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId);

    /**
     * 변경분 적용
     * @param bids 변경된 매수 주문 목록
     * @param asks 변경된 매도 주문 목록
     * @param updateId 업데이트 ID
     */
    void applyDiffs(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId);

    /**
     * 스냅샷과 과거 Diff 데이터를 사용하여 오더북을 복구
     * @param snapshot 스냅샷 메시지
     * @param diffs 버퍼에 저장해둔 최근 Diff 메시지 리스트
     */
    void restoreFromSnapshotAndDiffs(OrderBookMessage.SnapshotMessage snapshot, List<OrderBookMessage.DiffMessage> diffs);
}
