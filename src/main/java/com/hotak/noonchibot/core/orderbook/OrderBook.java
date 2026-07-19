package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@RequiredArgsConstructor
public class OrderBook {
    private Long lastDiffUid;
    private Long snapshotUid;
    private final boolean dex;
    private final NavigableMap<BigDecimal, OrderBookEntry> bidBook = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, OrderBookEntry> askBook = new TreeMap<>();
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal lastTradePrice;
    private Instant lastTradeTime;

    public List<OrderBookEntry> getBidEntries() {
        return List.copyOf(bidBook.values());
    }

    public List<OrderBookEntry> getAskEntries() {
        return List.copyOf(askBook.values());
    }

    public Long getSnapshotId() {
        return snapshotUid;
    }

    public Long getLastDiffId() {
        return lastDiffUid;
    }

    public BigDecimal getBestBid() {
        return bestBid;
    }

    public BigDecimal getBestAsk() {
        return bestAsk;
    }

    public BigDecimal getLastTradePrice() {
        return lastTradePrice;
    }

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
        this.lastDiffUid = null;
    }

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

    public void applyTrade(BigDecimal lastTradePrice, Instant timestamp) {
        lastTradeTime = timestamp;
        this.lastTradePrice = lastTradePrice;
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
                OrderBookEntry updatedAsk = new OrderBookEntry(bestAsk.updateId(), bestAskPrice, remainAsk);
                askBook.put(bestAskPrice, updatedAsk);
            }
            else {
                // 매수 잔량이 더 많음 -> 매도는 전부 삭제, 매수는 잔량 업데이트
                askBook.pollFirstEntry(); // 매도 삭제
                // 매수 잔량 차감
                BigDecimal remainBid = bidAmount.subtract(askAmount);
                OrderBookEntry updatedBid = new OrderBookEntry(bestBid.updateId(), bestBidPrice, remainBid);
                bidBook.put(bestBidPrice, updatedBid);
            }
        }
    }

    public void restoreFromSnapshotAndDiffs(OrderBookEvent.SnapshotReceived snapshot, List<OrderBookEvent.DiffReceived> diffs) {
        this.applySnapshot(snapshot.bids(), snapshot.asks(), snapshot.updateId());
        // (스냅샷 시점 이후의 데이터만 재적용)
        diffs.stream()
                .filter(diff -> diff.updateId() > snapshot.updateId())
                .forEach(diff -> this.applyDiffs(diff.bids(), diff.asks(), diff.updateId()));
    }

    public BigDecimal getBestPrice(boolean isBuy) {
        return isBuy ? bestAsk : bestBid;
    }

    public OrderBookQueryResult getImpactPriceForBaseVolume(boolean isBuy, BigDecimal volume) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;
        BigDecimal bestPrice = isBuy ? bestAsk : bestBid;

        BigDecimal cumulativeVolume = BigDecimal.ZERO;

        for (OrderBookEntry entry : book.values()) {
            cumulativeVolume = cumulativeVolume.add(entry.amount());
            if (cumulativeVolume.compareTo(volume) >= 0) {
                return new OrderBookQueryResult(bestPrice, volume, entry.price(), volume);
            }
        }
        BigDecimal lastPrice = book.isEmpty() ? null : book.lastEntry().getValue().price();
        return new OrderBookQueryResult(bestPrice, volume, lastPrice, cumulativeVolume);
    }

    public VWAPForVolumeQueryResult getVWAPForBaseVolume(boolean isBuy, BigDecimal volume) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal lastPrice = null;

        for (OrderBookEntry entry : book.values()) {
            BigDecimal remainingVolume = volume.subtract(totalVolume);
            lastPrice = entry.price();

            if (entry.amount().compareTo(remainingVolume) >= 0) {
                totalCost = totalCost.add(remainingVolume.multiply(entry.price()));
                totalVolume = volume;
                BigDecimal vwap = totalCost.divide(totalVolume, 8, RoundingMode.HALF_UP);
                return new VWAPForVolumeQueryResult(vwap, totalVolume, lastPrice);
            }
            totalCost = totalCost.add(entry.amount().multiply(entry.price()));
            totalVolume = totalVolume.add(entry.amount());
        }

        BigDecimal vwap = totalVolume.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(totalVolume, 8, RoundingMode.HALF_UP)
                : null;
        return new VWAPForVolumeQueryResult(vwap, totalVolume, lastPrice);
    }

    public VWAPForVolumeQueryResult getVWAPForQuoteVolume(boolean isBuy, BigDecimal quoteVolume) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalBaseVolume = BigDecimal.ZERO;
        BigDecimal lastPrice = null;

        for (OrderBookEntry entry : book.values()) {
            BigDecimal remainingQuote = quoteVolume.subtract(totalCost);
            BigDecimal entryCost = entry.amount().multiply(entry.price());
            lastPrice = entry.price();

            if (entryCost.compareTo(remainingQuote) >= 0) {
                BigDecimal fillBase = remainingQuote.divide(entry.price(), 8, RoundingMode.HALF_UP);
                totalBaseVolume = totalBaseVolume.add(fillBase);
                totalCost = quoteVolume;
                BigDecimal vwap = totalCost.divide(totalBaseVolume, 8, RoundingMode.HALF_UP);
                return new VWAPForVolumeQueryResult(vwap, totalBaseVolume, lastPrice);
            }

            totalCost = totalCost.add(entryCost);
            totalBaseVolume = totalBaseVolume.add(entry.amount());
        }

        BigDecimal vwap = totalBaseVolume.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(totalBaseVolume, 8, RoundingMode.HALF_UP)
                : null;
        return new VWAPForVolumeQueryResult(vwap, totalBaseVolume, lastPrice);
    }

    public OrderBookQueryResult getImpactPriceForQuoteVolume(boolean isBuy, BigDecimal quoteVolume) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;
        BigDecimal bestPrice = isBuy ? bestAsk : bestBid;

        BigDecimal cumulativeQuote = BigDecimal.ZERO;

        for (OrderBookEntry entry : book.values()) {
            BigDecimal levelQuote = entry.amount().multiply(entry.price());
            cumulativeQuote = cumulativeQuote.add(levelQuote);

            if (cumulativeQuote.compareTo(quoteVolume) >= 0) {
                return new OrderBookQueryResult(bestPrice, quoteVolume, entry.price(), quoteVolume);
            }
        }
        // 호가 부족
        BigDecimal lastPrice = book.isEmpty() ? null : book.lastEntry().getValue().price();
        return new OrderBookQueryResult(bestPrice, quoteVolume, lastPrice, cumulativeQuote);
    }

    public OrderBookQueryResult getQuoteVolumeForBaseVolume(boolean isBuy, BigDecimal baseAmount) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;

        BigDecimal cumulativeBase = BigDecimal.ZERO;
        BigDecimal cumulativeQuote = BigDecimal.ZERO;

        for (OrderBookEntry entry : book.values()) {
            BigDecimal remaining = baseAmount.subtract(cumulativeBase);
            BigDecimal fillAmount = entry.amount().min(remaining);

            cumulativeBase = cumulativeBase.add(fillAmount);
            cumulativeQuote = cumulativeQuote.add(fillAmount.multiply(entry.price()));

            if (cumulativeBase.compareTo(baseAmount) >= 0) {
                return new OrderBookQueryResult(null, baseAmount, null, cumulativeQuote);
            }
        }
        return new OrderBookQueryResult(null, baseAmount, null, cumulativeQuote);
    }

    public OrderBookQueryResult getVolumeForPrice(boolean isBuy, BigDecimal price) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;

        BigDecimal cumulativeVolume = BigDecimal.ZERO;
        BigDecimal resultPrice = null;

        for (OrderBookEntry entry : book.values()) {
            boolean outOfRange = isBuy
                    ? entry.price().compareTo(price) > 0
                    : entry.price().compareTo(price) < 0;

            if (outOfRange) {
                break;
            }
            cumulativeVolume = cumulativeVolume.add(entry.amount());
            resultPrice = entry.price();
        }
        return new OrderBookQueryResult(price, null, resultPrice, cumulativeVolume);
    }

    public OrderBookQueryResult getQuoteVolumeForPrice(boolean isBuy, BigDecimal price) {
        NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;

        BigDecimal cumulativeQuote = BigDecimal.ZERO;
        BigDecimal resultPrice = null;

        for (OrderBookEntry entry : book.values()) {
            boolean outOfRange = isBuy
                    ? entry.price().compareTo(price) > 0
                    : entry.price().compareTo(price) < 0;

            if (outOfRange) {
                break;
            }
            cumulativeQuote = cumulativeQuote.add(entry.amount().multiply(entry.price()));
            resultPrice = entry.price();
        }
        return new OrderBookQueryResult(price, null, resultPrice, cumulativeQuote);
    }

    public Instant getLastAppliedTradeTime() {
        return lastTradeTime;
    }
}
