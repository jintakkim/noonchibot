package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.RetryableTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
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

    private final String domain;
    private final OrderBookTrackerDataSource dataSource;
    private final Set<String> tradingPairs = new ConcurrentHashMap<>().newKeySet();


    private volatile Boolean isRunning = false;
    private final CountDownLatch initializedLatch = new CountDownLatch(1);

    private TaskExecutor generalExecutor;
    private final Map<String, TaskExecutor> pairExecutor = new ConcurrentHashMap<>();

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> savedMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> pastDiffsWindows = new ConcurrentHashMap<>();

    private final OrderBookTrackerMetrics metrics = new OrderBookTrackerMetrics();

    public OrderBookTracker(OrderBookTrackerDataSource dataSource, List<String> pairs, String domain) {
        this.domain = domain;
        this.dataSource = dataSource;
        this.tradingPairs.addAll(pairs);
        this.taskScheduler = taskScheduler;
    }

    public void start() {
        if (isRunning) stop();
        isRunning = true;

        log.info("OrderBookTracker 시작 중...");
        metrics.setTrackerStartTime(Instant.now());

        generalExecutor = Executors.newVirtualThreadPerTaskExecutor();

        initOrderBooks();

        dataSource.processOrderBookDiffs(this::onDiffReceived);
        dataSource.processOrderBookSnapshots(this::onSnapshotReceived);
        dataSource.processTrades(this::onTradeReceived);

        RetryableTrigger priceUpdateTrigger = new RetryableTrigger(PRICE_CHECK_INTERVAL, ERROR_RETRY_INTERVAL);
        scheduledTasks.add(scheduler.schedule(() -> {
            if (!isRunning) return;
            try {
                updateLastTradePrices();
                priceUpdateTrigger.recordSuccess();
            } catch (Exception e) {
                log.error("최근 거래 가격 업데이트 중 에러 발생 ({}초 후 재시도): {}", ERROR_RETRY_INTERVAL.toSeconds(), e.getMessage());
                priceUpdateTrigger.recordFailure();
            }
        }, priceUpdateTrigger));
    }

    public void stop() {
        isRunning = false;

        pairExecutor.values().forEach(ExecutorService::shutdown);
        pairExecutor.clear();

        log.info("OrderBookTracker가 정지되었습니다.");
    }

    public void waitReady() {
        try {
            initializedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OrderBook 준비 대기 중 인터럽트가 발생했습니다.");
        }
    }

    private void onDiffReceived(OrderBookMessage msg) {
        if (!isRunning) return;

        Instant start = Instant.now();
        String pair = msg.getTradingPair();

        OrderBook book = orderBooks.get(pair);
        OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

        // 아직 트래킹 전인 pair는 Queue에 임시 저장
        if (!pairExecutor.containsKey(pair)) {
            metrics.incrementTotalDiffsQueued();
            savedMessageQueues
                    .computeIfAbsent(pair, k -> new ConcurrentLinkedDeque<>())
                    .add(msg);
            return;
        }

        // 최신 id가 아닌 diff 메시지는 reject
        if (book.getSnapshotId() > msg.getUpdateId()) {
            metrics.incrementTotalDiffsRejected();
            pairMetrics.incrementDiffsRejected();
            return;
        }

        // pair 전용 executor에 처리 위임 → pair 내 순서 보장
        submitToPair(pair, () -> {
            book.applyDiffs(msg.getBids(), msg.getAsks(), msg.getUpdateId());
        });

        Instant now = Instant.now();
        Duration latency = Duration.between(start, now);

        metrics.recordDiffProcessed(latency);
        pairMetrics.recordDiffProcessed(latency, start);
    }

    private void onSnapshotReceived(OrderBookMessage msg) {
        if (!isRunning) return;

        Instant start = Instant.now();
        String pair = msg.getTradingPair();

        OrderBook book = orderBooks.get(pair);

        // 현재 트래킹 중이 아닌 페어를 받을 시 reject
        if (!pairExecutor.containsKey(pair)) {
            metrics.incrementTotalSnapshotsRejected();
            return;
        }

        submitToPair(pair, () -> {
            book.restoreFromSnapshotAndDiffs(msg.getBids(), msg.getAsks());;
        });

        Instant now = Instant.now();
        Duration latency = Duration.between(start, now);

        metrics.recordSnapshotProcessed(latency);
        metrics.getOrCreatePairMetrics(pair).recordSnapshotProcessed(latency, start);
    }

    private void onTradeReceived(OrderBookMessage msg) {
        if (!isRunning) return;

        Instant start = Instant.now();
        String pair = msg.getTradingPair();

        OrderBook book = orderBooks.get(pair);
        OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

        if (!pairExecutor.containsKey(pair)) {
            metrics.incrementTotalTradesRejected();
            pairMetrics.incrementTradesRejected();
            return;
        }

        submitToPair(pair, () -> {
            book.applyTrade(msg.getBids(), msg.getAsks(), msg.getUpdateId());
        });

        Instant now = Instant.now();
        Duration latency = Duration.between(start, now);

        metrics.recordTradeProcessed(latency);
        metrics.getOrCreatePairMetrics(pair).recordTradeProcessed(latency, start);
    }

    private void submitToPair(String pair, Runnable task) {
        ExecutorService pairExec = pairExecutor.get(pair);
        if (pairExec != null && !pairExec.isShutdown()) {
            pairExec.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("{} 오더북 처리 중 오류가 발생했습니다: {}", pair, e.getMessage(), e);
                }
            });
        }
    }

    private void initOrderBooks() {
        for (String pair : tradingPairs) {
            try {
                OrderBook book = dataSource.getNewOrderBook(pair);
                orderBooks.put(pair, book);

                ExecutorService pairExec = createPairExecutor(pair);
                pairExecutor.put(pair, pairExec);

                // 임시 저장된 메시지 flush
                drainSavedMessages(pair);

                log.info("{} 오더북 초기화 완료", pair);
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("{} 초기화 중 오류가 발생했습니다: {}", pair, e.getMessage(), e);
            }
        }
        initializedLatch.countDown();
    }

    private void drainSavedMessages(String pair) {
        Deque<OrderBookMessage> saved = savedMessageQueues.remove(pair);
        if (saved == null) return;

        OrderBookMessage msg;
        while ((msg = saved.pollFirst()) != null) {
            final OrderBookMessage m = msg;
            submitToPair(pair, () -> {
                switch (m.getType()) {
                    case DIFF -> orderBooks.get(pair).applyDiffs(m);
                    case SNAPSHOT -> orderBooks.get(pair).restoreFromSnapshotAndDiffs(m);
                    case TRADE -> orderBooks.get(pair).applyTrade(m);
                    default -> log.warn("{}에 대해 알 수 없는 메시지 유형: {}", pair, m.getType());
                }
            });
        }
    }

    public boolean addTradingPair(String pair) {
        if (orderBooks.containsKey(pair)) {
            log.warn("해당 페어는 이미 트래킹 중입니다.");
            return false;
        }

        waitReady();

        try {
            log.info("오더북 트래커에 {} 페어 추가 중...", pair);

            boolean subscribeSuccess = dataSource.subscribeToTradingPair(pair);
            if (!subscribeSuccess) {
                log.error("페어 연결에 실패했습니다.");
                return false;
            }

            if (!tradingPairs.contains(pair)) {
                tradingPairs.add(pair);
            }

            OrderBook book = dataSource.getNewOrderBook(pair);
            orderBooks.put(pair, book);

            ExecutorService pairExec = createPairExecutor(pair);
            pairExecutor.put(pair, pairExec);

            drainSavedMessages(pair);

            log.info("페어 연결에 성공했습니다.");
            return true;

        } catch (Exception e) {
            log.error("트레이딩 페어 {} 추가 중 오류 발생: {}", pair, e.getMessage(), e);
            removeTradingPair(pair);
            return false;
        }
    }

    public boolean removeTradingPair(String pair) {
        if (!orderBooks.containsKey(pair)) {
            log.warn("해당 페어는 트래킹 중이 아닙니다.");
            return false;
        }

        try {
            log.info("오더북 트래커에서 {} 삭제 중...", pair);

            // pair 전용 executor 종료
            ExecutorService pairExec = pairExecutor.remove(pair);
            if (pairExec != null) {
                pairExec.shutdownNow();
            }

            boolean unsubscribeSuccess = dataSource.unsubscribeFromTradingPair(pair);
            if (!unsubscribeSuccess) {
                log.warn("페어 삭제에 실패했습니다.");
            }

            orderBooks.remove(pair);
            pastDiffsWindows.remove(pair);
            savedMessageQueues.remove(pair);
            tradingPairs.remove(pair);

            metrics.removePairMetrics(pair);

            log.info("페어 삭제에 성공했습니다.");
            return true;

        } catch (Exception e) {
            log.error("트레이딩 페어 {} 삭제 중 오류 발생: {}", pair, e.getMessage(), e);
            return false;
        }
    }

    private void updateLastTradePrices() {
        if (initializedLatch.getCount() > 0) return;

        double now = Instant.now().toEpochMilli() / 1000.0;
        List<String> outdatedPairs = new ArrayList<>();

        for (OrderBook book : orderBooks.values()) {
            if (book.getLastAppliedTradeTime() < now - 180.0 &&
                    book.getLastTradePriceUpdatedTime() < now - 5.0) {
                outdatedPairs.add(book.getTradingPair());
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