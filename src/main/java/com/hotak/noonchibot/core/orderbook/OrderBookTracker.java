package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.OrderBookMessageStream;
import com.hotak.noonchibot.core.RetryableTrigger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class OrderBookTracker {
    private static final Duration PRICE_CHECK_INTERVAL = Duration.ofSeconds(1);
    private static final Duration ERROR_RETRY_INTERVAL = Duration.ofSeconds(30);

    /// 스냅샷 복구 시 restoreFromSnapshotAndDiffs에 전달할 diff 메시지 윈도우
    private final Map<String, Deque<OrderBookMessage.DiffMessage>> pastDiffsWindows = new HashMap<>();
    private final OrderBookDataSource dataSource;

    private final TaskScheduler scheduler;
    private final AsyncTaskExecutor executor;
    private final Map<String, Future<?>> executorTask = new ConcurrentHashMap<>();
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, OrderBookMessageStream> streams = new ConcurrentHashMap<>();

    public OrderBookTracker(OrderBookDataSource dataSource, TaskScheduler scheduler, AsyncTaskExecutor executor) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
        this.executor = executor;
        scheduleStalePriceFallback();
    }

    private void scheduleStalePriceFallback() {
        RetryableTrigger priceUpdateTrigger = new RetryableTrigger(PRICE_CHECK_INTERVAL, ERROR_RETRY_INTERVAL);
        scheduler.schedule(() -> {
            try {
                updateLastTradePrices();
                priceUpdateTrigger.recordSuccess();
            } catch (Exception e) {
                log.error("최근 거래 가격 업데이트 중 에러 발생: {}", e.getMessage());
                priceUpdateTrigger.recordFailure();
            }
        }, priceUpdateTrigger);
    }

    private Future<?> submitTaskToExecutor(Runnable runnable) {
        return executor.submit(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("스트림 처리 중 예외 발생: {}", e.getMessage(), e);
            }
        });
    }

    void processStream(OrderBookMessageStream stream) throws InterruptedException {
        OrderBookMessage message = stream.take();
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
        pastDiffsWindows.computeIfAbsent(tradingPair, k -> new ArrayDeque<>()).add(diffMessage);
        // 최신 id가 아닌 diff 메시지는 ignore
        if (book.getSnapshotId() > diffMessage.getUpdateId()) return;
        book.applyDiffs(diffMessage.getBids(), diffMessage.getAsks(), diffMessage.getUpdateId());
    }

    private void processSnapshotStream(OrderBookMessage.SnapshotMessage snapshotMessage) {
        String tradingPair = snapshotMessage.getTradingPair();
        OrderBook book = orderBooks.get(tradingPair);
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        List<OrderBookMessage.DiffMessage> pastDiffs = new ArrayList<>(pastDiffsWindows.getOrDefault(tradingPair, new ArrayDeque<>()));
        book.restoreFromSnapshotAndDiffs(snapshotMessage, pastDiffs);
    }

    private void processTradeStream(OrderBookMessage.TradeMessage tradeMessage) {
        String tradingPair = tradeMessage.getTradingPair();
        if (!orderBooks.containsKey(tradingPair)) return; // 현재 트래킹 중이 아닌 페어를 받을 시 ignore
        OrderBook book = orderBooks.get(tradingPair);
        book.applyTrade(tradeMessage);
    }

    public void addTradingPair(String tradingPair) {
        if (orderBooks.containsKey(tradingPair)) {
            log.warn("해당 페어는 이미 트래킹 중입니다.");
            return;
        }
        OrderBookMessageStream stream = dataSource.subscribe(tradingPair);
        streams.put(tradingPair, stream);
        executorTask.put(tradingPair, submitTaskToExecutor(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processStream(stream);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }));
        OrderBook book = dataSource.getNewOrderBook(tradingPair);
        orderBooks.put(tradingPair, book);
    }

    public void removeTradingPair(String tradingPair) {
        if (!orderBooks.containsKey(tradingPair)) {
            log.warn("해당 페어는 트래킹 중이 아닙니다.");
            return;
        }
        orderBooks.remove(tradingPair);
        OrderBookMessageStream stream = streams.remove(tradingPair);
        dataSource.unsubscribe(stream);
    }

    /**
     * orderBook websocket 연결 끊김시 lastTradePrice는 별도로 restApi로 조회 후 업데이트
     * 마지막 trade msg 수신 시점으로 부터 대략 3분이 지났다면 끊김으로 판단(PRICE_CHECK_INTERVAL에 따라 변동가능).
     */
    void updateLastTradePrices() {
        Set<String> stalePairs = orderBooks.entrySet().stream()
                .filter(e -> isStale(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (stalePairs.isEmpty()) return;
        Map<String, BigDecimal> lastPrices = dataSource.getLastTradedPrices(stalePairs);
        lastPrices.forEach((tradingPair, price) -> {
            OrderBook orderBook = orderBooks.get(tradingPair);
            if(orderBook == null) return;
            orderBook.setLastTradePrice(price);
        });
    }

    private boolean isStale(OrderBook book) {
        Instant lastTradeTime = book.getLastAppliedTradeTime();
        return lastTradeTime == null || lastTradeTime.isBefore(Instant.now().minus(Duration.ofMinutes(3)));
    }

    public Map<String, ReadOnlyOrderBook> getReadOnlyOrderBooks() {
        return new HashMap<>(orderBooks);
    }
}