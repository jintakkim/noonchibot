package com.hotak.noonchibot.core.orderbook;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class OrderBookTracker implements SmartLifecycle {
    private static final int MAX_PAST_DIFFS = 30;

    /// 스냅샷 복구 시 restoreFromSnapshotAndDiffs에 전달할 diff 메시지 윈도우
    private final Map<String, Deque<OrderBookMessage.DiffMessage>> pastDiffsWindows = new HashMap<>();
    private final OrderBookDataSource dataSource;

    private final IoExecutor ioExecutor;
    private final MainExecutor mainExecutor;
    private final Map<String, OrderBook> orderBooks = new HashMap<>();
    private final Map<String, OrderBookMessageStream> streams = new HashMap<>();
    private final Map<String, Future<?>> streamTasks = new HashMap<>();
    private final String platformName;

    private volatile boolean running = false;

    public OrderBookTracker(
            OrderBookDataSource dataSource,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            String platformName
    ) {
        this.dataSource = dataSource;
        this.mainExecutor = mainExecutor;
        this.ioExecutor = ioExecutor;
        this.platformName = platformName;
    }

    @VisibleForTesting
    void processMessage(OrderBookMessage message) {
        switch (message) {
            case OrderBookMessage.SnapshotMessage s -> processSnapshotStream(s);
            case OrderBookMessage.DiffMessage d -> processDiffStream(d);
            case OrderBookMessage.TradeMessage t -> processTradeStream(t);
            default -> throw new IllegalStateException("Unexpected value: " + message);
        }
    }

    private void processDiffStream(OrderBookMessage.DiffMessage diffMessage) {
        String tradingPair = diffMessage.getTradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        // 최신 id가 아닌 diff 메시지는 ignore
        if (book.getSnapshotId() > diffMessage.getUpdateId()) return;
        Deque<OrderBookMessage.DiffMessage> window = pastDiffsWindows.computeIfAbsent(tradingPair, k -> new ArrayDeque<>());
        window.add(diffMessage);
        while (window.size() > MAX_PAST_DIFFS) {
            window.poll();
        }
        book.applyDiffs(diffMessage.getBids(), diffMessage.getAsks(), diffMessage.getUpdateId());
    }

    private void processSnapshotStream(OrderBookMessage.SnapshotMessage snapshotMessage) {
        String tradingPair = snapshotMessage.getTradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        List<OrderBookMessage.DiffMessage> pastDiffs = new ArrayList<>(pastDiffsWindows.getOrDefault(tradingPair, new ArrayDeque<>()));
        book.restoreFromSnapshotAndDiffs(snapshotMessage, pastDiffs);
    }

    private void processTradeStream(OrderBookMessage.TradeMessage tradeMessage) {
        String tradingPair = tradeMessage.getTradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        book.applyTrade(tradeMessage);
    }

    /**
     * blocking method
     * do not call this method at mainExecutor
     */
    public void addTradingPair(String tradingPair) {
        addTradingPairAsync(tradingPair).join();
    }

    public CompletableFuture<Void> addTradingPairAsync(String tradingPair) {
        if (orderBooks.containsKey(tradingPair)) {
            log.warn("해당 페어는 이미 트래킹 중입니다.");
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture
                .supplyAsync(() -> dataSource.getNewOrderBook(tradingPair), ioExecutor)
                .thenAcceptAsync(book -> {
                    orderBooks.put(tradingPair, book);
                    OrderBookMessageStream stream = dataSource.subscribeOrderBookStream(tradingPair);
                    streams.put(tradingPair, stream);
                    Future<?> task = ioExecutor.submit(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            try {
                                OrderBookMessage message = stream.take();
                                mainExecutor.submit(() -> processMessage(message));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                    streamTasks.put(tradingPair, task);
                }, mainExecutor);
    }

    public void removeTradingPair(String tradingPair) {
        if (!orderBooks.containsKey(tradingPair)) {
            log.warn("해당 페어는 트래킹 중이 아닙니다.");
            return;
        }
        orderBooks.remove(tradingPair);
        OrderBookMessageStream stream = streams.remove(tradingPair);
        dataSource.unsubscribe(stream);
        Future<?> task = streamTasks.remove(tradingPair);
        if (task != null) task.cancel(true);
    }

    public Map<String, OrderBook> getOrderBooks() {
        return new HashMap<>(orderBooks);
    }

    public Optional<OrderBook> findOrderBook(String tradingPair) {
        return Optional.ofNullable(orderBooks.get(tradingPair));
    }

    @VisibleForTesting
    Deque<OrderBookMessage.DiffMessage> getPastDiffsWindow(String tradingPair) {
        return pastDiffsWindows.get(tradingPair);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        // 모든 stream 구독 해제
        streams.forEach((pair, stream) -> dataSource.unsubscribe(stream));
        // 모든 stream consumer 스레드 종료
        streamTasks.values().forEach(task -> task.cancel(true));
        streamTasks.clear();
        streams.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public String getPlatformName() {
        return platformName;
    }
}