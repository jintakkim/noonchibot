package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.OrderBookMessage;
import com.hotak.noonchibot.core.utils.TimeUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class OrderBookTracker {
    private static final Logger logger = Logger.getLogger(OrderBookTracker.class.getName());

    private final OrderBookTrackerDataSource dataSource;
    private final List<String> tradingPairs = new CopyOnWriteArrayList<>();

    // 핵심 데이터 구조
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage>> trackingQueues = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> trackingTasks = new ConcurrentHashMap<>();
    private final Map<String, Deque<com.hotak.noonchibot.core.datatype.OrderBookMessage>> savedMessageQueues = new ConcurrentHashMap<>();

    // 중앙 메시지 스트림
    private final BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage> diffStream = new LinkedBlockingQueue<>();
    private final BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage> snapshotStream = new LinkedBlockingQueue<>();
    private final BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage> tradeStream = new LinkedBlockingQueue<>();

    // 통계 및 상태 관리
    private final OrderBookTrackerMetrics metrics = new OrderBookTrackerMetrics();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final CountDownLatch initializedLatch = new CountDownLatch(1);
    private ExecutorService executor;

    public OrderBookTracker(OrderBookTrackerDataSource dataSource, List<String> pairs) {
        this.dataSource = dataSource;
        this.tradingPairs.addAll(pairs);
    }

    public void start() {
        if (isRunning.get()) stop();
        isRunning.set(true);

        logger.info("Starting OrderBookTracker...");
        metrics.setTrackerStartTime(TimeUtils.getCurrentSeconds());

        // 스레드 풀 초기화
        executor = Executors.newCachedThreadPool();

        // 1. 초기화 작업 시작
        executor.submit(this::initOrderBooks);

        // 2. 메시지 라우터 루프 시작
        executor.submit(this::orderBookDiffRouter);
        executor.submit(this::orderBookSnapshotRouter);
        executor.submit(this::emitTradeEventLoop);

        // 3. 데이터 소스 리스너 가동 (비동기 실행 가정)
        dataSource.listenForOrderBookDiffs(diffStream);
        dataSource.listenForOrderBookSnapshots(snapshotStream);
        dataSource.listenForTrades(tradeStream);
    }

    /**
     * Diff 메시지 라우터
     */
    private void orderBookDiffRouter() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                com.hotak.noonchibot.core.datatype.OrderBookMessage msg = diffStream.take();
                double start = TimeUtils.getCurrentSeconds();
                String pair = msg.getTradingPair();

                if (!trackingQueues.containsKey(pair)) {
                    metrics.incrementTotalDiffsQueued();
                    savedMessageQueues.computeIfAbsent(pair, k -> new ConcurrentLinkedDeque<>()).add(msg);
                    continue;
                }

                OrderBook book = orderBooks.get(pair);
                OrderBookPairMetrics pairMetrics = metrics.getOrCreatePairMetrics(pair);

                if (book.getSnapshotId() > msg.getUpdateId()) {
                    metrics.incrementTotalDiffsRejected();
                    pairMetrics.incrementDiffsRejected();
                    continue;
                }

                trackingQueues.get(pair).put(msg);

                // 통계 기록
                double latency = (TimeUtils.getCurrentSeconds() - start) * 1000;
                metrics.recordDiffProcessed(latency);
                pairMetrics.recordDiff(latency, start);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Snapshot 메시지 라우터
     */
    private void orderBookSnapshotRouter() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                com.hotak.noonchibot.core.datatype.OrderBookMessage msg = snapshotStream.take(); // snapshotStream에서 가져옴
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

    /**
     * Trade 메시지 라우터 (파이썬의 emit_trade_event_loop)
     */
    private void emitTradeEventLoop() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                com.hotak.noonchibot.core.datatype.OrderBookMessage msg = tradeStream.take();
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
        BlockingQueue<com.hotak.noonchibot.core.datatype.OrderBookMessage> queue = trackingQueues.get(pair);
        OrderBook book = orderBooks.get(pair);

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 1. 저장된 메시지(Snapshot 대기 중 쌓인 Diff)가 있으면 먼저 처리, 없으면 큐에서 대기
                Deque<com.hotak.noonchibot.core.datatype.OrderBookMessage> saved = savedMessageQueues.get(pair);
                OrderBookMessage msg = (saved != null && !saved.isEmpty()) ? saved.pollFirst() : queue.take();

                // 2. Switch 문을 이용한 타입별 처리 (Modern Java Switch Expressions)
                switch (msg.type) {
                    case DIFF -> book.applyDiffs(msg);
                    case SNAPSHOT -> book.restoreFromSnapshotAndDiffs(msg);
                    case TRADE -> {
                        // Trade는 보통 emitTradeEventLoop에서 처리하지만,
                        // 이 큐에 들어왔을 경우를 대비한 방어 코드
                        book.applyTrade(msg);
                    }
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

    public void stop() {
        isRunning.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
        logger.info("OrderBookTracker stopped.");
    }
}