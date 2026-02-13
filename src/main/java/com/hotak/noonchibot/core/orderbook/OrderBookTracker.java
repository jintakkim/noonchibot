package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.utils.TimeUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class OrderBookTracker {
    private static final Logger logger = Logger.getLogger(OrderBookTracker.class.getName());
    private static final int PAST_DIFF_WINDOW_SIZE = 32;

    private final String domain; // 거래소 환경 식별자 (Binance Global/US/Testnet)
    private final OrderBookTrackerDataSource dataSource; // 거래소 통신부
    private final List<String> tradingPairs = new CopyOnWriteArrayList<>(); // 감시 종목 리스트

    private final AtomicBoolean isRunning = new AtomicBoolean(false); // [필수] 중복 실행 방지 및 상태 스위치
    private final CountDownLatch initializedLatch = new CountDownLatch(1); // 초기화 완료까지 대기시키는 신호등
    private ExecutorService executor; // 모든 배경 작업(쓰레드)을 돌리는 엔진

    // [종목별 데이터 관리 박스]
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<OrderBookMessage>> trackingQueues = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> trackingTasks = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> savedMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, Deque<OrderBookMessage>> pastDiffsWindows = new ConcurrentHashMap<>();

    // [중앙 데이터 통로]
    private final BlockingQueue<OrderBookMessage> diffStream = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderBookMessage> snapshotStream = new LinkedBlockingQueue<>();
    private final BlockingQueue<OrderBookMessage> tradeStream = new LinkedBlockingQueue<>();

    // [성능 기록]
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
        metrics.setTrackerStartTime(TimeUtils.getCurrentSeconds());

        // 스레드 풀 초기화
        executor = Executors.newCachedThreadPool();
        executor.submit(this::initOrderBooks);

        //라우터 루프 시작
        executor.submit(this::orderBookDiffRouter);
        executor.submit(this::orderBookSnapshotRouter);
        executor.submit(this::orderBookTradeRouter);

        //데이터 소스 리스너 가동
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


    //모든 오더북에 대해 웹소켓 연결 불량으로 인해
    //최신 체결가가 stale 해지지 않도록 일정 시간 후 Rest 업데이트.
    private void updateLastTradePricesLoop() {
        this.waitReady();

        logger.info("Starting last trade price update loop.");

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            //페어에 대해 마지막으로 받은 데이터의 시간으로부터 흐른 시간 체크 (스테일 체크), 빈도 체크
            //3분을 넘겼다면 스테일로 판단, 아웃데이티드 리스트에 해당 페어를 추가한다
            try {
                double now = TimeUtils.getCurrentUnixTimestamp();
                List<String> outdatedPairs = new ArrayList<>();

                for (Map.Entry<String, OrderBook> entry : orderBooks.entrySet()) {
                    String pair = entry.getKey();
                    OrderBook book = entry.getValue();

                    if (book.getLastAppliedTradeTime() < now - 180.0 &&
                            book.getLastTradePriceRestUpdatedTime() < now - 5.0) {
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
                    Thread.sleep(1000);
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

    /**
     * 실시간으로 새로운 거래 페어를 추가합니다.
     */
    public boolean addTradingPair(String pair) {
        if (orderBooks.containsKey(pair)) {
            logger.warning("해당 페어는 이미 트래킹 중입니다.");
            return false;
        }

        // 초기 오더북들이 다 준비될 때까지 대기
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

    /**
     * 실행 중인 특정 거래 페어의 추적을 중단하고 모든 데이터를 삭제합니다.
     */
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

            // 2. 웹소켓 구독 해지
            boolean unsubscribeSuccess = dataSource.unsubscribeFromTradingPair(pair);
            if (!unsubscribeSuccess) {
                logger.warning("Failed to unsubscribe from " + pair);
            }

            // 3. 데이터 구조 정리 (메모리 누수 방지)
            orderBooks.remove(pair);
            trackingQueues.remove(pair);
            pastDiffsWindows.remove(pair);
            savedMessageQueues.remove(pair);
            tradingPairs.remove(pair);

            // 4. 메트릭 삭제 (구현되어 있다면)
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
                double start = TimeUtils.getCurrentSeconds();
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

                // 통계 기록
                double latency = (TimeUtils.getCurrentSeconds() - start) * 1000;
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
                double start = TimeUtils.getCurrentSeconds();
                String pair = msg.getTradingPair();

                if (!trackingQueues.containsKey(pair)) {
                    metrics.incrementTotalSnapshotsRejected();
                    continue;
                }

                trackingQueues.get(pair).put(msg);

                // 통계 기록
                double latency = (TimeUtils.getCurrentSeconds() - start) * 1000;
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
                double start = TimeUtils.getCurrentSeconds();
                String pair = msg.getTradingPair();

                if (!orderBooks.containsKey(pair)) {
                    metrics.incrementTotalTradesRejected();
                    continue;
                }

                // 오더북에 즉시 반영
                orderBooks.get(pair).applyTrade(msg);

                // 통계 기록
                double latency = (TimeUtils.getCurrentSeconds() - start) * 1000;
                metrics.recordTradeProcessed(latency);
                metrics.getOrCreatePairMetrics(pair).recordTradeProcessed(latency, start);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 개별 오더북 메시지 처리 워커
     */
    private void trackSingleBook(String pair) {
        BlockingQueue<OrderBookMessage> queue = trackingQueues.get(pair);
        OrderBook book = orderBooks.get(pair);

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 1. 저장된 메시지(Snapshot 대기 중 쌓인 Diff)가 있으면 먼저 처리, 없으면 큐에서 대기
                Deque<OrderBookMessage> saved = savedMessageQueues.get(pair);
                OrderBookMessage msg = (saved != null && !saved.isEmpty()) ? saved.pollFirst() : queue.take();

                // 2. Switch 문을 이용한 타입별 처리 (Modern Java Switch Expressions)
                switch (msg.type) {
                    case DIFF -> book.applyDiffs(msg);
                    case SNAPSHOT -> book.restoreFromSnapshotAndDiffs(msg);
                    case TRADE -> book.applyTrade(msg);
                    default -> logger.warning("Unknown message type received for " + pair + ": " + msg.getType());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // 인터럽트 발생 시 루프 종료
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