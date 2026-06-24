package com.hotak.noonchibot.core.orderbook;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class OrderBookTracker implements OrderedLifecycleAware {
    private static final int MAX_PAST_DIFFS = 50;
    /// diff 메시지 버퍼
    private final Map<String, Deque<OrderBookEvent.DiffReceived>> pastDiffsWindows = new HashMap<>();
    private final Map<String, OrderBook> orderBooks = new HashMap<>();
    private final EventSubscriber eventSubscriber;
    private final EventPublisher eventPublisher;
    private final boolean isDex;
    private final Set<Subscription> subscriptions = new HashSet<>();
    private final Set<String> pairsToSubscribe;

    @VisibleForTesting
    void processDiff(OrderBookEvent.DiffReceived event, EventMetadata eventMetadata) {
        String tradingPair = event.tradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        Deque<OrderBookEvent.DiffReceived> window = pastDiffsWindows.computeIfAbsent(tradingPair, k -> new ArrayDeque<>());
        window.add(event);
        while (window.size() > MAX_PAST_DIFFS) {
            window.poll();
        }
        Long snapshotId = book.getSnapshotId();
        if (snapshotId == null) {
            return;
        }
        Long currentId = book.getLastDiffId() != null ? book.getLastDiffId() : snapshotId;
        if (event.updateId() <= currentId) {
            return;
        }
        book.applyDiffs(event.bids(), event.asks(), event.updateId());
    }

    @VisibleForTesting
    void processSnapshot(OrderBookEvent.SnapshotReceived event, EventMetadata eventMetadata) {
        String tradingPair = event.tradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        List<OrderBookEvent.DiffReceived> pastDiffs = new ArrayList<>(pastDiffsWindows.getOrDefault(tradingPair, new ArrayDeque<>()));
        book.restoreFromSnapshotAndDiffs(event, pastDiffs);
    }

    @VisibleForTesting
    void processTrade(OrderBookEvent.TradeReceived event, EventMetadata eventMetadata) {
        String tradingPair = event.tradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        book.applyTrade(event.price(), event.timestamp());
    }

    public void addTradingPair(String tradingPair) {
        OrderBook previous = orderBooks.putIfAbsent(tradingPair, new OrderBook(isDex));
        if (previous != null) {
            log.warn("해당 페어는 이미 트래킹 중입니다.");
            return;
        }
        OrderBook book = new OrderBook(isDex);
        orderBooks.put(tradingPair, book);
        eventPublisher.publish(new OrderBookEvent.TrackingRequested(tradingPair));
    }

    public Map<String, OrderBook> getOrderBooks() {
        return new HashMap<>(orderBooks);
    }

    public Optional<OrderBook> findOrderBook(String tradingPair) {
        return Optional.ofNullable(orderBooks.get(tradingPair));
    }

    @Override
    public void onStart() {
        subscriptions.add(
                eventSubscriber.subscribe(OrderBookEvent.DiffReceived.class, this::processDiff, ExecutionPolicy.sequential())
        );
        subscriptions.add(
                eventSubscriber.subscribe(OrderBookEvent.TradeReceived.class, this::processTrade, ExecutionPolicy.sequential())
        );
        subscriptions.add(
                eventSubscriber.subscribe(OrderBookEvent.SnapshotReceived.class, this::processSnapshot, ExecutionPolicy.sequential())
        );
        pairsToSubscribe.forEach(this::addTradingPair);
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public int phase() {
        return Phases.ORDER_BOOK_TRACKER_SETUP;
    }
}