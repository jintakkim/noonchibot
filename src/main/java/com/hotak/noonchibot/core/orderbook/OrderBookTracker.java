package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class OrderBookTracker {
    private static final Logger logger = Logger.getLogger(OrderBookTracker.class.getName());
    private static final int PAST_DIFF_WINDOW_SIZE = 32;

    private final String domain;
    private final OrderBookTrackerDataSource dataSource;
    private final List<String> tradingPairs = new CopyOnWriteArrayList<>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
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
        if (isRunning.get()) stop();
        isRunning.set(true);

        logger.info("Starting OrderBookTracker...");
        metrics.setTrackerStartTime(Instant.now().toEpochMilli() / 1000.0);

        executor = Executors.newCachedThreadPool();
        executor.submit(this::initOrderBooks);

        executor.submit(this::orderBookDiffRouter);
        executor.submit(this::orderBookSnapshotRouter);
        executor.submit(this::orderBookTradeRouter);

        dataSource.listenForOrderBookDiffs(diffStream);
        dataSource.listenForOrderBookSnapshots(snapshotStream);
        dataSource.listenForTrades(tradeStream);
    }

    public void stop() {
        isRunning.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
        logger.info("OrderBookTracker stopped.");
    }

    public void waitReady() {
        try {
            initializedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("오더북 준비 대기 중 인터럽트 발생");
        }
    }

    private void updateLastTradePricesLoop() {
        this.waitReady();

        logger.info("Starting last trade price update loop.");

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
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
                logger.severe("Unexpected error while fetching last trade price: " + e.getMessage());
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
            logger.warning("해당 페어는 이미 트래킹 중입니다.");
            return false;
        }

        waitReady();

        try {
            logger.info("오더북 트래커에" + pair + "추가 중...");

            boolean subscribeSuccess = dataSource.subscribeToTradingPair(pair);
            if (!subscribeSuccess) {
                logger.severe("페어 연결에 실패했습니다.");
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

            logger.info("Successfully added trading pair " + pair);
            return true;

        } catch (Exception e) {
            logger.severe("Error adding trading pair " + pair + ": " + e.getMessage());
            removeTradingPair(pair);
            return false;
        }
    }

    public boolean removeTradingPair(String pair) {
        if (!orderBooks.containsKey(pair)) {
            logger.warning("해당 페어는 트래킹 중이 아닙니다.");
            return false;
        }

        try {
            logger.info("오더북 트래커에" + pair + "삭제 중...");

            Future<?> task = trackingTasks.remove(pair);
            if (task != null) {
                task.cancel(true);
            }

            boolean unsubscribeSuccess = dataSource.unsubscribeFromTradingPair(pair);
            if (!unsubscribeSuccess) {
                logger.warning("Failed to unsubscribe from " + pair);
            }

            orderBooks.remove(pair);
            trackingQueues.remove(pair);
            pastDiffsWindows.remove(pair);
            savedMessageQueues.remove(pair);
            tradingPairs.remove(pair);

            metrics.removePairMetrics(pair);

            logger.info("Successfully removed trading pair " + pair);
            return true;

        } catch (Exception e) {
            logger.severe("Error removing trading pair " + pair + ": " + e.getMessage());
            return false;
        }
    }

    private void orderBookDiffRouter() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                OrderBookMessage msg = diffStream.take();
                long start = System.nanoTime();
                String pair = msg.

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
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
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
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
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

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Deque<OrderBookMessage> saved = savedMessageQueues.get(pair);
                OrderBookMessage msg = (saved != null && !saved.isEmpty()) ? saved.pollFirst() : queue.take();

                switch (msg.type) {
                    case DIFF -> book.applyDiffs(msg);
                    case SNAPSHOT -> book.restoreFromSnapshotAndDiffs(msg);
                    case TRADE -> book.applyTrade(msg);
                    default -> logger.warning("Unknown message type received for " + pair + ": " + msg.getType());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.severe("Error tracking order book for " + pair + ": " + e.getMessage());
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

                logger.info("Initialized order book for " + pair);
                Thread.sleep(100);
            } catch (Exception e) {
                logger.severe("Error initializing " + pair + ": " + e.getMessage());
            }
        }
        initializedLatch.countDown();
    }

    public OrderBookTrackerMetrics getMetrics() {
        return metrics;
    }
}