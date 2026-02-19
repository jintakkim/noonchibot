package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.RetryableTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class OrderBookTracker {
    private static final Duration PRICE_CHECK_INTERVAL = Duration.ofSeconds(1);
    private static final Duration ERROR_RETRY_INTERVAL = Duration.ofSeconds(30);
    private static final Duration TRADE_STALE_THRESHOLD = Duration.ofSeconds(180);
    private static final Duration PRICE_UPDATE_THRESHOLD = Duration.ofSeconds(5);

    private final String domain;
    private final OrderBookTrackerDataSource dataSource;
    private final Set<String> tradingPairs = ConcurrentHashMap.newKeySet();

    private volatile Boolean isRunning = false;
    private volatile CountDownLatch initializedLatch = new CountDownLatch(1);

    private final TaskScheduler scheduler;
    private final AsyncTaskExecutor executor;
    private final List<ScheduledFuture<?>> scheduledTasks = new CopyOnWriteArrayList<>();
    private final List<Future<?>> streamTasks = new ArrayList<>();

    private final BlockingQueue<OrderBookMessage> diffQueue;
    private final BlockingQueue<OrderBookMessage> snapshotQueue;
    private final BlockingQueue<OrderBookMessage> tradeQueue;

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> savedMessageQueues = new ConcurrentHashMap<>();

    private final OrderBookTrackerMetrics metrics = new OrderBookTrackerMetrics();

    public OrderBookTracker(OrderBookTrackerDataSource dataSource, List<String> pairs, String domain,
                            TaskScheduler scheduler, AsyncTaskExecutor executor,
                            BlockingQueue<OrderBookMessage> diffQueue,
                            BlockingQueue<OrderBookMessage> snapshotQueue,
                            BlockingQueue<OrderBookMessage> tradeQueue) {
        this.domain = domain;
        this.dataSource = dataSource;
        this.tradingPairs.addAll(pairs);
        this.scheduler = scheduler;
        this.executor = executor;
        this.diffQueue = diffQueue;
        this.snapshotQueue = snapshotQueue;
        this.tradeQueue = tradeQueue;
    }

    public void start() {
        if (isRunning) return;
        initializedLatch = new CountDownLatch(1);
        isRunning = true;

        log.info("OrderBookTracker 시작 중...");
        metrics.setTrackerStartTime(Instant.now());

        initOrderBooks();

        dataSource.listenToOrderBookDiffs(diffQueue);
        dataSource.listenToOrderBookSnapshots(snapshotQueue);
        dataSource.listenToTrades(tradeQueue);

        streamTasks.add(executor.submit(() -> {
            try {
                processDiffStream(diffQueue);
            } catch (Exception e) {
                log.error("Diff 스트림 처리 중 예외 발생: {}", e.getMessage(), e);
            }
        }));
        streamTasks.add(executor.submit(() -> {
            try {
                processSnapshotStream(snapshotQueue);
            } catch (Exception e) {
                log.error("Snapshot 스트림 처리 중 예외 발생: {}", e.getMessage(), e);
            }
        }));
        streamTasks.add(executor.submit(() -> {
            try {
                processTradeStream(tradeQueue);
            } catch (Exception e) {
                log.error("Trade 스트림 처리 중 예외 발생: {}", e.getMessage(), e);
            }
        }));

        RetryableTrigger priceUpdateTrigger = new RetryableTrigger(PRICE_CHECK_INTERVAL, ERROR_RETRY_INTERVAL);
        ScheduledFuture<?> scheduledTask = scheduler.schedule(() -> {
            try {
                updateLastTradePrices();
                priceUpdateTrigger.recordSuccess();
            } catch (Exception e) {
                log.error("최근 거래 가격 업데이트 중 에러 발생: {}", e.getMessage());
                priceUpdateTrigger.recordFailure();
            }
        }, priceUpdateTrigger);

        scheduledTasks.add(scheduledTask);
    }

    public void stop() {
        isRunning = false;

        streamTasks.forEach(task -> task.cancel(true));
        streamTasks.clear();

        scheduledTasks.forEach(task -> task.cancel(true));
        scheduledTasks.clear();

        orderBooks.clear();
        savedMessageQueues.clear();

        log.info("OrderBookTracker가 정지되었습니다.");
    }

    public void waitReady() {
        try {
            initializedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processDiffStream(BlockingQueue<OrderBookMessage> diffStream) {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = diffStream.take();
                String pair = msg.getTradingPair();
                Instant start = Instant.now();

                OrderBook book = orderBooks.get(pair);
                OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

                // 아직 트래킹 전인 pair는 Queue에 임시 저장
                if (!orderBooks.containsKey(pair)) {
                    metrics.incrementTotalDiffsQueued();
                    savedMessageQueues
                            .computeIfAbsent(pair, k -> new ConcurrentLinkedDeque<>())
                            .add(msg);
                    continue;
                }

                // 최신 id가 아닌 diff 메시지는 reject
                if (book.getSnapshotId() > msg.getUpdateId()) {
                    metrics.incrementTotalDiffsRejected();
                    pairMetrics.incrementDiffsRejected();
                    continue;
                }

                // pair 전용 executor에 처리 위임 → pair 내 순서 보장 / 동기 실행
                book.applyDiffs(msg.getBids(), msg.getAsks(), msg.getUpdateId());

                Instant now = Instant.now();
                Duration latency = Duration.between(start, now);

                metrics.recordDiffProcessed(latency);
                pairMetrics.recordDiffProcessed(latency, start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processSnapshotStream(BlockingQueue<OrderBookMessage> snapshotStream) {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = snapshotStream.take();
                String pair = msg.getTradingPair();
                Instant start = Instant.now();

                OrderBook book = orderBooks.get(pair);
                OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

                // 현재 트래킹 중이 아닌 페어를 받을 시 reject
                if (!orderBooks.containsKey(pair)) {
                    metrics.incrementTotalSnapshotsRejected();
                    return;
                }

                book.restoreFromSnapshotAndDiffs(msg.getBids(), msg.getAsks());

                Instant now = Instant.now();
                Duration latency = Duration.between(start, now);

                metrics.recordSnapshotProcessed(latency);
                pairMetrics.recordSnapshotProcessed(latency, start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processTradeStream(BlockingQueue<OrderBookMessage> tradeStream) {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = tradeStream.take();
                String pair = msg.getTradingPair();
                Instant start = Instant.now();

                OrderBook book = orderBooks.get(pair);
                OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

                if (!orderBooks.containsKey(pair)) {
                    metrics.incrementTotalTradesRejected();
                    pairMetrics.incrementTradesRejected();
                    return;
                }

                /// Trade 메시지 구현 필요
                /// book.applyTrade(msg.getPrice(), msg.getAmount(), msg.getTradeId(), msg.getTimestamp());

                Instant now = Instant.now();
                Duration latency = Duration.between(start, now);

                metrics.recordTradeProcessed(latency);
                metrics.getOrCreatePairMetrics(pair).recordTradeProcessed(latency, start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initOrderBooks() {
        for (String pair : tradingPairs) {
            OrderBook book = dataSource.getNewOrderBook(pair);
            orderBooks.put(pair, book);

            drainSavedMessages(pair);

            log.info("{} 오더북 초기화 완료", pair);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        initializedLatch.countDown();
    }

    private void drainSavedMessages(String pair) {
        Deque<OrderBookMessage> saved = savedMessageQueues.remove(pair);
        if (saved == null) return;

        OrderBook book = orderBooks.get(pair);
        OrderBookMessage msg;
        while ((msg = saved.pollFirst()) != null) {
            switch (msg.getType()) {
                case DIFF -> book.applyDiffs(msg.getBids(), msg.getAsks(), msg.getUpdateId());
                case SNAPSHOT -> book.restoreFromSnapshotAndDiffs(msg.getBids(), msg.getAsks());
                /// case TRADE -> book.applyTrade(msg.getPrice(), msg.getAmount(), msg.getTradeId(), msg.getTimestamp());
                default -> log.warn("{}에 대해 알 수 없는 메시지 유형: {}", pair, msg.getType());
            }
        }
    }

    public void addTradingPair(String pair) {
        if (orderBooks.containsKey(pair)) {
            log.warn("해당 페어는 이미 트래킹 중입니다.");
            return;
        }

        waitReady();

        log.info("오더북 트래커에 {} 페어 추가 중...", pair);

        dataSource.subscribeToTradingPair(pair);

        tradingPairs.add(pair);

        OrderBook book = dataSource.getNewOrderBook(pair);
        orderBooks.put(pair, book);

        drainSavedMessages(pair);

        log.info("{} 페어 추가 완료", pair);
    }

    public void removeTradingPair(String pair) {
        if (!orderBooks.containsKey(pair)) {
            log.warn("해당 페어는 트래킹 중이 아닙니다.");
            return;
        }

        waitReady();

        log.info("오더북 트래커에서 {} 삭제 중...", pair);

        dataSource.unsubscribeFromTradingPair(pair);

        orderBooks.remove(pair);
        savedMessageQueues.remove(pair);
        tradingPairs.remove(pair);
        metrics.removePairMetrics(pair);

        log.info("{} 페어 삭제 완료", pair);
    }

    private void updateLastTradePrices() {
        if (initializedLatch.getCount() > 0) return;

        Instant now = Instant.now();
        Set<String> outdatedPairs = new HashSet<>();

        for (Map.Entry<String, OrderBook> entry : orderBooks.entrySet()) {
            String pair = entry.getKey();
            OrderBook book = entry.getValue();

            if (Duration.between(book.getLastAppliedTradeTime(), now).compareTo(TRADE_STALE_THRESHOLD) > 0 &&
                    Duration.between(book.getLastTradePriceUpdatedTime(), now).compareTo(PRICE_UPDATE_THRESHOLD) > 0) {
                outdatedPairs.add(pair); // Map의 Key인 pair를 추가
            }
        }

        if (!outdatedPairs.isEmpty()) {
            Map<String, BigDecimal> lastPrices = dataSource.getLastTradedPrices(outdatedPairs, domain);

            lastPrices.forEach((pair, price) -> {
                OrderBook book = orderBooks.get(pair);
                if (book != null) book.setLastTradePrice(price);
            });
        }
    }
}