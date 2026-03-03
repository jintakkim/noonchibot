package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.RetryableTrigger;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderUpdate;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.orderbook.ReadOnlyOrderBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractExchangeConnector extends AbstractConnector implements ExchangeConnector {
    private static final Duration SHORT_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration LONG_POLL_INTERVAL = Duration.ofMinutes(2);
    private static final Duration TICK_INTERVAL_LIMIT = Duration.ofMinutes(1);
    private static final Duration TRADING_RULES_INTERVAL = Duration.ofMinutes(30);
    private static final Duration TRADING_FEES_INTERVAL = Duration.ofHours(12);
    private static final Duration ERROR_RETRY_INTERVAL = Duration.ofMillis(500);

    protected volatile Map<String, TradingRule> tradingRules;
    private final UserStreamTracker userStreamTracker;
    private final OrderIdGenerator orderIdGenerator;
    private final OrderBookTracker orderBookTracker;
    private final OrderTracker orderTracker;
    private final TaskScheduler scheduler;
    private final AsyncTaskExecutor executor;
    private final Semaphore pollSemaphore = new Semaphore(0);
    private final RestAssistant restAssistant;
    private final String checkNetworkRequestPath;
    private final String tradingRulesRequestPath;
    private final String tradingPairRequestPath;
    /**
     * 취소주문이 동기적으로 처리되는지 여부
     */
    private final boolean isCancelRequestInExchangeSynchronous;
    /**
     * 클라이언트에서 임의로 정하는 id에 대해 prefix
     */
    private final String clientOrderIdPrefix;
    /**
     * 거래소에서 설정한 id 최대 길이
     */
    private final int clientOrderIdMaxLength;

    protected final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final OrderBookDataSource orderBookDataSource;
    private Future<?> pollStatusFuture;
    private Future<?> userStreamFuture;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
    private Instant lastTimestamp = Instant.MIN;


    public AbstractExchangeConnector(
            String name,
            FeeEstimator feeEstimator,
            Map<String, Map<String, BigDecimal>> balanceLimit,
            UserStreamTracker userStreamTracker,
            OrderIdGenerator orderIdGenerator,
            OrderBookTracker orderBookTracker,
            OrderTracker orderTracker,
            TaskScheduler scheduler,
            AsyncTaskExecutor executor,
            RestAssistant restAssistant,
            String checkNetworkRequestPath,
            String tradingRulesRequestPath,
            String tradingPairRequestPath,
            boolean isCancelRequestInExchangeSynchronous,
            String clientOrderIdPrefix,
            int clientOrderIdMaxLength,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookDataSource orderBookDataSource

    ) {
        super(name, feeEstimator, balanceLimit);
        this.userStreamTracker = userStreamTracker;
        this.orderIdGenerator = orderIdGenerator;
        this.orderBookTracker = orderBookTracker;
        this.orderTracker = orderTracker;
        this.scheduler = scheduler;
        this.executor = executor;
        this.restAssistant = restAssistant;
        this.checkNetworkRequestPath = checkNetworkRequestPath;
        this.tradingRulesRequestPath = tradingRulesRequestPath;
        this.tradingPairRequestPath = tradingPairRequestPath;
        this.isCancelRequestInExchangeSynchronous = isCancelRequestInExchangeSynchronous;
        this.clientOrderIdPrefix = clientOrderIdPrefix;
        this.clientOrderIdMaxLength = clientOrderIdMaxLength;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.orderBookDataSource = orderBookDataSource;
    }

    protected abstract String getPrivateRestUrl(String pathUrl, String domain);

    protected abstract String getPublicRestUrl(String pathUrl, String domain);


    protected ReadOnlyOrderBook getReadOnlyOrderBook(String tradingPair) {
        ReadOnlyOrderBook orderBook = orderBookTracker.getReadOnlyOrderBooks().get(tradingPair);
        if (orderBook == null)
            throw new IllegalArgumentException("No order book found for trading pair: " + tradingPair);
        return orderBook;
    }

    @Override
    public boolean isReady() {
        return !tradingPairSymbolRegistry.isEmpty() &&
                !accountBalances.isEmpty() &&
                !tradingRules.isEmpty() &&
                userStreamTracker.isRunning();

    }

    @Override
    public abstract Set<OrderType> getSupportedOrderType(String tradingPair);

    /**
     * 요청 에러가 시간 동기화 문제인지 확인
     */
    protected abstract boolean isRequestExceptionRelatedToTimeSynchronizer(Exception e);

    /**
     * 주문 상태 조회시 주문 없음 예외인지 확인
     */
    protected abstract boolean isOrderNotFoundDuringStatusUpdateException(Exception e);

    /**
     * 주문 취소시 주문 없음 예외인지 확인
     */
    protected abstract boolean isOrderNotFoundDuringCancellationException(Exception e);

    @Override
    public BigDecimal getOrderPriceQuantum(String tradingPair, BigDecimal price) {
        TradingRule tradingRule = tradingRules.get(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");
        return tradingRule.minPriceIncrement();
    }

    @Override
    public BigDecimal getOrderSizeQuantum(String tradingPair, BigDecimal orderSize) {
        TradingRule tradingRule = tradingRules.get(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");
        return tradingRule.minBaseAmountIncrement();
    }

    @Override
    public void onTick(Instant timestamp) {
        super.onTick(timestamp);
        pollStatusIfNeeded();
        lastTimestamp = timestamp;
    }

    @Override
    public String buy(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Object... args) {
        String clientOrderId = orderIdGenerator.createClientOrderId(true, tradingPair, clientOrderIdPrefix, clientOrderIdMaxLength);
        createOrder(TradeType.BUY, clientOrderId, tradingPair, orderType, amount, price, args);
        return clientOrderId;
    }

    @Override
    public String sell(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Object... args) {
        String clientOrderId = orderIdGenerator.createClientOrderId(false, tradingPair, clientOrderIdPrefix, clientOrderIdMaxLength);
        createOrder(TradeType.SELL, clientOrderId, tradingPair, orderType, amount, price, args);
        return clientOrderId;
    }

    private void createOrder(TradeType tradeType, String orderId, String tradingPair, OrderType orderType, BigDecimal amount, BigDecimal price, Object... args) {
        TradingRule tradingRule = tradingRules.get(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");

        BigDecimal quantizedPrice = price;
        if (orderType.equals(OrderType.LIMIT) || orderType.equals(OrderType.LIMIT_MAKER)) {
            quantizedPrice = quantizeOrderPrice(tradingPair, price);
        }
        BigDecimal quantizedOrderAmount = quantizeOrderAmount(tradingPair, amount);

        orderTracker.startTrackingOrder(orderId, null, tradingPair, orderType, tradeType, quantizedPrice, quantizedOrderAmount);
        InFlightOrder order = orderTracker.findActiveOrder(orderId).orElseThrow();

        if (!getSupportedOrderType(tradingPair).contains(orderType)) {
            updateOrderAfterFailure(orderId, tradingPair, "해당 오더 타입은 지원하지 않습니다.");
            return;
        }

        if (quantizedOrderAmount.compareTo(tradingRule.minOrderSize()) < 0) {
            updateOrderAfterFailure(orderId, tradingPair, "주문 수량이 최소 주문 수량보다 커야합니다.");
            return;
        }

        BigDecimal notionalSize = price == null ? orderBookDataSource.getLastTradedPrice(tradingPair).multiply(quantizedOrderAmount) : quantizedPrice.multiply(quantizedOrderAmount);
        if (notionalSize.compareTo(tradingRule.minNotionalSize()) < 0) {
            updateOrderAfterFailure(orderId, tradingPair, "주문 금액이 최소 주문 금액보다 커야합니다.");
            return;
        }
        try {
            placeOrderAndProcessUpdate(order, args);
        } catch (Exception e) {
            onOrderFailure(orderId, tradingPair, e);
        }
    }

    @Override
    public void cancel(String tradingPair, String clientOrderId) {
        InFlightOrder trackedOrder = orderTracker.findTrackedOrder(clientOrderId).orElse(null);
        if (trackedOrder == null) {
            log.warn("orderId: {}에 해당하는 주문을 찾을 수 없습니다.", clientOrderId);
        }

        try {
            placeCancel(clientOrderId, trackedOrder);
            OrderState newState = isCancelRequestInExchangeSynchronous ? OrderState.CANCELED : OrderState.PENDING_CANCEL;
            OrderUpdate orderUpdate = new OrderUpdate(tradingPair, getCurrentTimestamp(), newState, clientOrderId, null);
            orderTracker.updateOrder(orderUpdate);
        } catch (Exception e) {
            if(isOrderNotFoundDuringCancellationException(e)) {
                log.warn("orderId: {}에 해당하는 주문을 찾을 수 없습니다.", clientOrderId);
                orderTracker.processOrderNotFound(clientOrderId);
                return;
            }
            log.error("주문을 취소하는데 실패 헀습니다", e);
        }
    }

    /**
     * 최우선 호가 리턴
     */
//    public BigDecimal getBestPrice(String tradingPair, boolean isBuy) {
//        OrderBook orderBook = getOrderBook(tradingPair);
//        BigDecimal topPrice = orderBook.getBestPrice(isBuy);
//        return quantizeOrderPrice(tradingPair, topPrice);
//    }

//    public BigDecimal getMidPrice(String tradingPair) {
//        return getBestPrice(tradingPair, true).add(getBestPrice(tradingPair, false)).divide(BigDecimal.TWO, RoundingMode.HALF_UP);
//    }

    private void pollStatusIfNeeded() {
        long intervalMs = getStatusPollInterval(getCurrentTimestamp()).toMillis();
        long lastTick = lastTimestamp.toEpochMilli() / intervalMs;
        long currentTick = getCurrentTimestamp().toEpochMilli() / intervalMs;
        if (currentTick > lastTick) pollStatusFuture = executor.submit(() -> {
            try {
                pollStatus();
            } catch (Exception e) {
                log.error("polling 방식의 status 업데이트 도중 예외 발생", e);
            }
        });
    }

    /**
     * 폴링 주기를 결정한다.
     * WebSocket이 정상이면 긴 주기(백업용), 끊겼으면 짧은 주기로 폴링
     *
     * @param timestamp 현재 시간
     * @return 폴링 주기
     */
    private Duration getStatusPollInterval(Instant timestamp) {
        Instant lastUserStreamMessageTime = userStreamTracker == null ? Instant.MIN : userStreamTracker.getLastRecvTime();
        Duration lastRecvDiff = Duration.between(lastUserStreamMessageTime, timestamp);
        // WebSocket 메시지가 60초 이상 없으면 → 5초마다 폴링
        // WebSocket 정상이면 → 120초마다 폴링
        return lastRecvDiff.compareTo(TICK_INTERVAL_LIMIT) > 0 ? SHORT_POLL_INTERVAL : LONG_POLL_INTERVAL;
    }

    private String placeOrderAndProcessUpdate(InFlightOrder order, Object... args) {
        OrderPlacedDto placedOrder = placeOrder(order.getClientOrderId(), order.getTradingPair(), order.getAmount(), order.getTradeType(), order.getOrderType(), order.getPrice(), args);
        OrderUpdate orderUpdate = new OrderUpdate(order.getTradingPair(), placedOrder.timestamp(), OrderState.OPEN, order.getClientOrderId(), placedOrder.exchangeOrderId());
        orderTracker.updateOrder(orderUpdate);
        return placedOrder.exchangeOrderId();
    }

    /**
     * 동기적으로 주문 처리
     */
    protected abstract OrderPlacedDto placeOrder(String orderId, String tradingPair, BigDecimal amount, TradeType tradeType, OrderType orderType, BigDecimal price, Object... args);

    private void updateOrderAfterFailure(String orderId, String tradingPair, String message) {
        OrderUpdate.FailedOrderUpdate orderUpdate = new OrderUpdate.FailedOrderUpdate(tradingPair, getCurrentTimestamp(), orderId, null, message);
        orderTracker.updateOrder(orderUpdate);
    }

    private void onOrderFailure(String orderId, String tradingPair, Exception e) {
        log.error("{}에 대한 주문을 제출하는데 실패 했습니다, 네트워크 에러나 거래소 서버 상태, apiKey 문제 일 수 있습니다.", orderId);
        updateOrderAfterFailure(orderId, tradingPair, e.getMessage());
    }

    /**
     * 동기적으로 취소 처리
     * @return 성공시 true 실패시 false
     */
    protected abstract boolean placeCancel(String orderId, InFlightOrder trackedOrder);

    @Override
    protected void startNetwork() {
        stopNetwork();
        userStreamTracker.start();

        //주기적 트레이딩 룰 업데이트 스케줄러 등록
        RetryableTrigger tradingRuleUpdateTrigger = new RetryableTrigger(TRADING_RULES_INTERVAL, ERROR_RETRY_INTERVAL);
        scheduledTasks.add(scheduler.schedule(() -> {
            try {
                updateTradingRules();
                tradingRuleUpdateTrigger.recordSuccess();
            } catch (Exception e) {
                log.error("error occurred while updating trading rule", e);
                tradingRuleUpdateTrigger.recordFailure();
            }
        }, tradingRuleUpdateTrigger));

        //주기적 거래 수수료 업데이트 스케줄러 등록
        RetryableTrigger tradingFeeUpdateTrigger = new RetryableTrigger(TRADING_FEES_INTERVAL, ERROR_RETRY_INTERVAL);
        scheduledTasks.add(scheduler.schedule(() -> {
            try {
                updateTradingFees();
                tradingFeeUpdateTrigger.recordSuccess();
            } catch (Exception e) {
                log.error("error occurred while updating trading fee", e);
                tradingFeeUpdateTrigger.recordFailure();
            }
        }, tradingFeeUpdateTrigger));

        listenUserStream();
    }

    /**
     * 상태 폴링
     */
    private void pollStatus() {
        updateTimeSynchronizer();
        CompletableFuture<Void> balancesFuture = CompletableFuture.runAsync(this::updateBalances, executor);
        CompletableFuture<Void> ordersFuture = CompletableFuture.runAsync(this::updateOrders, executor);
        CompletableFuture.allOf(balancesFuture, ordersFuture).join();
    }


    private void listenUserStream() {
        userStreamFuture = executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Object event = userStreamTracker.userStream.take();
                    processUserStreamEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void updateTradingRules() {
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(tradingRulesRequestPath)
                        .authRequired(false)
                        .params(Map.of("symbols", tradingPairSymbolRegistry.getAllExchangeSymbols()))
                        .build()
        );
        this.tradingRules = parseTradingRule(body).stream().collect(Collectors.toMap(TradingRule::tradingPair, Function.identity()));
    }
    protected abstract List<TradingRule> parseTradingRule(JsonNode node);

    protected abstract void updateTradingFees();
    protected abstract void updateBalances();
    protected abstract void updateTimeSynchronizer();
    protected abstract void updateOrders();
    protected abstract void processUserStreamEvent(Object event);

    @Override
    protected void stopNetwork() {
        lastTimestamp = null;
        userStreamTracker.stop();
        scheduledTasks.forEach(task -> task.cancel(true));
        if (pollStatusFuture != null) pollStatusFuture.cancel(true);
        if (userStreamFuture != null) userStreamFuture.cancel(true);
    }

    @Override
    protected NetworkStatus checkNetwork() {
        try {
            String url = getApiRequestUrl(checkNetworkRequestPath, false);
            restAssistant.executeRequestAndGetResponse(
                    RestRequest.builder()
                            .method(HttpMethod.GET)
                            .pathUrl(checkNetworkRequestPath)
                            .authRequired(false)
                            .build()
            );
            return NetworkStatus.CONNECTED;
        } catch (Exception e) {
            log.warn("network check failed", e);
            return NetworkStatus.NOT_CONNECTED;
        }
    }

    private String getApiRequestUrl(String pathUrl, boolean isAuthRequired) {
        if(isAuthRequired) return getPrivateRestUrl(pathUrl, domain);
        return getPublicRestUrl(pathUrl, domain);
    }
}
