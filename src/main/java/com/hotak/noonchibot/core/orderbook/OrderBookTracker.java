package com.hotak.noonchibot.core.orderbook;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class OrderBookTracker {
    private final String domain;
    private final OrderBookTrackerDataSource dataSource;
    private final List<String> tradingPairs = new CopyOnWriteArrayList<>();

    private volatile Boolean isRunning = false;
    private final CountDownLatch initializedLatch = new CountDownLatch(1);
    private ExecutorService executor;

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<OrderBookMessage>> trackingQueues = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> trackingTasks = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> savedMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> pastDiffsWindows = new ConcurrentHashMap<>();

    private final BlockingQueue<OrderBookMessage> diffStream = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderBookMessage> snapshotStream = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderBookMessage> tradeStream = new LinkedBlockingQueue<>();

    private final OrderBookTrackerMetrics metrics = new OrderBookTrackerMetrics();

    public OrderBookTracker(OrderBookTrackerDataSource dataSource, List<String> pairs, String domain) {
        this.dataSource = dataSource;
        this.tradingPairs.addAll(pairs);
        this.domain = domain;
    }

    public void start() {
        if (isRunning) stop();
        isRunning = true;

        log.info("OrderBookTracker 시작 중...");
        metrics.setTrackerStartTime(Instant.now().toEpochMilli() / 1000.0);

        executor = Executors.newVirtualThreadPerTaskExecutor();

        initOrderBooks();

        executor.submit(this::orderBookDiffRouter);
        executor.submit(this::orderBookSnapshotRouter);
        executor.submit(this::orderBookTradeRouter);

        dataSource.listenForOrderBookDiffs(diffStream);
        dataSource.listenForOrderBookSnapshots(snapshotStream);
        dataSource.listenForTrades(tradeStream);
    }

    public void stop() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
        }
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

    private void updateLastTradePricesLoop() {
        this.waitReady();

        log.info("LastTradePrices 업데아트 루프 시작 중..");

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                double now = Instant.now().toEpochMilli() / 1000.0;
                List<String> outdatedPairs = new ArrayList<>();

                for (Map.Entry<String, OrderBook> entry : orderBooks.entrySet()) {
                    String pair = entry.getKey();
                    OrderBook book = entry.getValue();

                    if (book.getLastAppliedTradeTime() < now - 180.0 &&
                            book.getLastTradePriceRestUpdatedTime() < now - 5.0) { // 이름수정, 시간 부등호 비교 금지
                        outdatedPairs.add(pair);
                    }
                }

                if (!outdatedPairs.isEmpty()) {
                    Map<String, BigDecimal> lastPrices = dataSource.getLastTradedPrices(outdatedPairs, domain);

                    for (Map.Entry<String, BigDecimal> priceEntry : lastPrices.entrySet()) {
                        String pair = priceEntry.getKey();
                        BigDecimal lastPrice = priceEntry.getValue();

                        OrderBook book = orderBooks.get(pair);
                        if (book != null) {
                            book.setLastTradePrice(lastPrice);
                        }
                    }
                } else {
                    Thread.sleep(1000); //task
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("최근 거래가를 가져오는 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
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

            trackingQueues.put(pair, new LinkedBlockingQueue<>());
            Future<?> task = executor.submit(() -> trackSingleBook(pair));
            trackingTasks.put(pair, task);

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

            Future<?> task = trackingTasks.remove(pair);
            if (task != null) {
                task.cancel(true);
            }

            boolean unsubscribeSuccess = dataSource.unsubscribeFromTradingPair(pair);
            if (!unsubscribeSuccess) {
                log.warn("페어 삭제에 실패했습니다.");
            }

            orderBooks.remove(pair);
            trackingQueues.remove(pair);
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

    private void orderBookDiffRouter() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = diffStream.take();
                long start = System.nanoTime();
//                String pair = msg.

                if (!trackingQueues.containsKey(pair)) {
                    metrics.incrementTotalDiffsQueued();
                    savedMessageQueues.computeIfAbsent(pair, k -> new ConcurrentLinkedDeque<>()).add(msg);
                    continue;
                }

                OrderBook book = orderBooks.get(pair);
                OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

                if (book.getSnapshotId() > msg.getUpdateId()) {
                    metrics.incrementTotalDiffsRejected();
                    pairMetrics.incrementTotalDiffsRejected();
                    continue;
                }

                trackingQueues.get(pair).put(msg);

                double latency = (System.nanoTime() - start) / 1_000_000.0;
                metrics.recordDiffProcessed(latency);
                pairMetrics.recordDiffProcessed(latency, start);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void orderBookSnapshotRouter() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = snapshotStream.take();
                long start = System.nanoTime();
                String pair = msg.getTradingPair();

                if (!trackingQueues.containsKey(pair)) {
                    metrics.incrementTotalSnapshotsRejected();
                    continue;
                }

                trackingQueues.get(pair).put(msg);

                double latency = (System.nanoTime() - start) / 1_000_000.0;
                metrics.recordSnapshotProcessed(latency);
                metrics.getOrCreatePairMetrics(pair).recordSnapshotProcessed(latency, start);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void orderBookTradeRouter() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = tradeStream.take();
                double start = System.nanoTime();
                String pair = msg.getTradingPair();

                if (!orderBooks.containsKey(pair)) {
                    metrics.incrementTotalTradesRejected();
                    continue;
                }

                orderBooks.get(pair).applyTrade(msg);

                double latency = (System.nanoTime() - start) / 1_000_000.0;
                metrics.recordTradeProcessed(latency);
                metrics.getOrCreatePairMetrics(pair).recordTradeProcessed(latency, start);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void trackSingleBook(String pair) {
        BlockingQueue<OrderBookMessage> queue = trackingQueues.get(pair);
        OrderBook book = orderBooks.get(pair);

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                Deque<OrderBookMessage> saved = savedMessageQueues.get(pair);
                OrderBookMessage msg = (saved != null && !saved.isEmpty()) ? saved.pollFirst() : queue.take();

                switch (msg.type) {
                    case DIFF -> book.applyDiffs(msg);
                    case SNAPSHOT -> book.restoreFromSnapshotAndDiffs(msg);
                    case TRADE -> book.applyTrade(msg);
                    default -> log.warn("{}에 대해 알 수 없는 메시지 유형이 수신되었습니다: {}", pair, msg.getType());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{} 오더북 트래킹 중 오류가 발생했습니다: {}", pair, e.getMessage(), e);
            }
        }
    }

    private void initOrderBooks() {
        for (String pair : tradingPairs) {
            try {
                OrderBook book = dataSource.getNewOrderBook(pair);
                orderBooks.put(pair, book);
                trackingQueues.put(pair, new LinkedBlockingQueue<>());

                Future<?> task = executor.submit(() -> trackSingleBook(pair));
                trackingTasks.put(pair, task);

                log.info("{} 오더북 초기화 완료", pair);
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("{} 초기화 중 오류가 발생했습니다: {}", pair, e.getMessage(), e);
            }
        }
        initializedLatch.countDown();
    }

    public OrderBookTrackerMetrics getMetrics() {
        return metrics;
    }
}