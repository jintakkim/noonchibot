package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.PubSub;
import com.hotak.noonchibot.core.event.OrderBookTradeEvent;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@RequiredArgsConstructor
public abstract class AbstractOrderBook extends PubSub implements OrderBook {
    private volatile Long lastDiffUid;
    private volatile Long snapshotUid;
    private final boolean dex;
    private final NavigableMap<BigDecimal, OrderBookEntry> bidBook = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, OrderBookEntry> askBook = new TreeMap<>();
    private volatile BigDecimal bestBid;
    private volatile BigDecimal bestAsk;
    private volatile BigDecimal lastTradePrice;
    private volatile Instant lastTradeTime;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Override
    public List<OrderBookEntry> getBidEntries() {
        return withReadLock(() -> List.copyOf(bidBook.values()));
    }

    @Override
    public List<OrderBookEntry> getAskEntries() {
        return withReadLock(() -> List.copyOf(askBook.values()));
    }

    @Override
    public Long getSnapshotId() {
        return snapshotUid;
    }

    @Override
    public Long getLastDiffId() {
        return lastDiffUid;
    }

    @Override
    public BigDecimal getBestBid() {
        return bestBid;
    }

    @Override
    public BigDecimal getBestAsk() {
        return bestAsk;
    }

    @Override
    public BigDecimal getLastTradePrice() {
        return lastTradePrice;
    }

    @Override
    public void applySnapshot(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId) {
        withWriteLock(() -> {
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
        });
    }

    @Override
    public void applyDiffs(List<OrderBookEntry> bids, List<OrderBookEntry> asks, long updateId) {
        withWriteLock(() -> {
            this.lastDiffUid = updateId;
            for (OrderBookEntry entry : bids) {
                updateBook(bidBook, entry);
            }
            for (OrderBookEntry entry : asks) {
                updateBook(askBook, entry);
            }
            refreshBestPrices();
        });
    }

    @Override
    public void applyTrade(OrderBookMessage.TradeMessage message) {
        lastTradeTime = message.getTimestamp();
        this.lastTradePrice = message.getPrice();
        triggerEvent(new OrderBookTradeEvent(message.getTradingPair(), message.getPrice(), message.getAmount(), message.getTradeId(), message.getTimestamp()));
    }

    @Override
    public void setLastTradePrice(BigDecimal lastTradePrice) {
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

    @Override
    public void restoreFromSnapshotAndDiffs(OrderBookMessage.SnapshotMessage snapshot, List<OrderBookMessage.DiffMessage> diffs) {
        withWriteLock(() -> {
            this.applySnapshot(snapshot.getBids(), snapshot.getAsks(), snapshot.getUpdateId());
            // (스냅샷 시점 이후의 데이터만 재적용)
            diffs.stream()
                    .filter(diff -> diff.getUpdateId() > snapshot.getUpdateId())
                    .forEach(diff -> this.applyDiffs(diff.getBids(), diff.getAsks(), diff.getUpdateId()));
        });
    }

    @Override
    public BigDecimal getBestPrice(boolean isBuy) {
        return isBuy ? bestAsk : bestBid;
    }

    @Override
    public OrderBookQueryResult getImpactPriceForBaseVolume(boolean isBuy, BigDecimal volume) {
        return withReadLock(() -> {
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
        });
    }

    @Override
    public OrderBookQueryResult getVWAPForVolume(boolean isBuy, BigDecimal volume) {
        return withReadLock(() -> {
            NavigableMap<BigDecimal, OrderBookEntry> book = isBuy ? askBook : bidBook;
            BigDecimal bestPrice = isBuy ? bestAsk : bestBid;

            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalVolume = BigDecimal.ZERO;
            BigDecimal resultVwap = null;

            for (OrderBookEntry entry : book.values()) {
                BigDecimal remainingVolume = volume.subtract(totalVolume);

                if (entry.amount().compareTo(remainingVolume) >= 0) {
                    totalCost = totalCost.add(remainingVolume.multiply(entry.price()));
                    totalVolume = volume;
                    resultVwap = totalCost.divide(totalVolume, 8, RoundingMode.HALF_UP);

                    return new OrderBookQueryResult(bestPrice, volume, resultVwap, totalVolume);
                }
                totalCost = totalCost.add(entry.amount().multiply(entry.price()));
                totalVolume = totalVolume.add(entry.amount());
            }

            if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                resultVwap = totalCost.divide(totalVolume, 8, RoundingMode.HALF_UP);
            }
            return new OrderBookQueryResult(bestPrice, volume, resultVwap, totalVolume);
        });
    }

    @Override
    public OrderBookQueryResult getImpactPriceForQuoteVolume(boolean isBuy, BigDecimal quoteVolume) {
        return withReadLock(() -> {
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
        });
    }

    @Override
    public OrderBookQueryResult getQuoteVolumeForBaseVolume(boolean isBuy, BigDecimal baseAmount) {
        return withReadLock(() -> {
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
        });
    }

    @Override
    public OrderBookQueryResult getVolumeForPrice(boolean isBuy, BigDecimal price) {
        return withReadLock(() -> {
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
        });
    }

    @Override
    public OrderBookQueryResult getQuoteVolumeForPrice(boolean isBuy, BigDecimal price) {
        return withReadLock(() -> {
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
        });
    }

    @Override
    public Instant getLastAppliedTradeTime() {
        return lastTradeTime;
    }

    private <T> T withReadLock(Supplier<T> action) {
        readLock.lock();
        try {
            return action.get();
        } finally {
            readLock.unlock();
        }
    }

    private void withWriteLock(Runnable action) {
        writeLock.lock();
        try {
            action.run();
        } finally {
            writeLock.unlock();
        }
    }
}
