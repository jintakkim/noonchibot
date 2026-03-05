package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.RetryableTrigger;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.orderbook.ReadOnlyOrderBook;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractExchangeConnector extends AbstractConnector implements ExchangeConnector {
    protected static final Duration SHORT_POLL_INTERVAL = Duration.ofSeconds(5);
    protected static final Duration LONG_POLL_INTERVAL = Duration.ofMinutes(2);
    protected static final Duration TICK_INTERVAL_LIMIT = Duration.ofMinutes(1);
    protected static final Duration TRADING_RULES_INTERVAL = Duration.ofMinutes(30);
    protected static final Duration ERROR_RETRY_INTERVAL = Duration.ofMillis(500);

    protected volatile Map<String, TradingRule> tradingRules = Collections.emptyMap();
    private final UserStreamTracker userStreamTracker;
    private final OrderIdGenerator orderIdGenerator;
    protected final OrderBookTracker orderBookTracker;
    protected final OrderTracker orderTracker;
    private final TaskScheduler scheduler;
    protected final AsyncTaskExecutor executor;
    protected final RestAssistant restAssistant;
    private final String checkNetworkRequestPath;
    private final String tradingRulesRequestPath;
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
    protected Instant lastPollTimestamp = Instant.MIN;


    public AbstractExchangeConnector(
            String name,
            Map<String, BigDecimal> balanceLimit,
            TradeFeeSchemaLoader tradeFeeSchemaLoader,
            UserStreamTracker userStreamTracker,
            OrderIdGenerator orderIdGenerator,
            OrderBookTracker orderBookTracker,
            OrderTracker orderTracker,
            TaskScheduler scheduler,
            AsyncTaskExecutor executor,
            RestAssistant restAssistant,
            String checkNetworkRequestPath,
            String tradingRulesRequestPath,
            boolean isCancelRequestInExchangeSynchronous,
            String clientOrderIdPrefix,
            int clientOrderIdMaxLength,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookDataSource orderBookDataSource

    ) {
        super(name, balanceLimit, tradeFeeSchemaLoader);
        this.userStreamTracker = userStreamTracker;
        this.orderIdGenerator = orderIdGenerator;
        this.orderBookTracker = orderBookTracker;
        this.orderTracker = orderTracker;
        this.scheduler = scheduler;
        this.executor = executor;
        this.restAssistant = restAssistant;
        this.checkNetworkRequestPath = checkNetworkRequestPath;
        this.tradingRulesRequestPath = tradingRulesRequestPath;
        this.isCancelRequestInExchangeSynchronous = isCancelRequestInExchangeSynchronous;
        this.clientOrderIdPrefix = clientOrderIdPrefix;
        this.clientOrderIdMaxLength = clientOrderIdMaxLength;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.orderBookDataSource = orderBookDataSource;

    }

    protected ReadOnlyOrderBook getOrderBook(String tradingPair) {
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
    public Map<String, InFlightOrder> getInFlightOrders() {
        return orderTracker.getActiveOrders();
    }

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
    public List<String> getAllTradingPairs() {
        return tradingPairSymbolRegistry.getAllTradingPairs();
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

    private void createOrder(TradeType tradeType, String clientOrderId, String tradingPair, OrderType orderType, BigDecimal amount, BigDecimal price, Object... args) {
        TradingRule tradingRule = tradingRules.get(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");

        BigDecimal quantizedPrice = price;
        if (orderType.equals(OrderType.LIMIT) || orderType.equals(OrderType.LIMIT_MAKER)) {
            quantizedPrice = quantizeOrderPrice(tradingPair, price);
        }
        BigDecimal quantizedOrderAmount = quantizeOrderAmount(tradingPair, amount);

        InFlightOrder order = new InFlightOrder(clientOrderId, tradingPair, orderType, tradeType, amount, price, getCurrentTimestamp());
        orderTracker.startTrackingOrder(order);


        if (!getSupportedOrderType(tradingPair).contains(orderType)) {
            updateOrderAfterFailure(clientOrderId, tradingPair, new OrderValidationException.UnsupportedOrderTypeException("해당 오더 타입은 지원하지 않습니다."));
            return;
        }

        if (quantizedOrderAmount.compareTo(tradingRule.minOrderSize()) < 0) {
            updateOrderAfterFailure(clientOrderId, tradingPair, new OrderValidationException.BelowMinOrderSizeException("주문 수량이 최소 주문 수량보다 커야합니다."));
            return;
        }

        BigDecimal notionalSize = price == null ? orderBookDataSource.getLastTradedPrice(tradingPair).multiply(quantizedOrderAmount) : quantizedPrice.multiply(quantizedOrderAmount);
        if (notionalSize.compareTo(tradingRule.minNotionalSize()) < 0) {
            updateOrderAfterFailure(clientOrderId, tradingPair, new OrderValidationException.BelowMinNotionalException("주문 금액이 최소 주문 금액보다 커야합니다."));
            return;
        }
        try {
            placeOrderAndProcessUpdate(order, args);
        } catch (Exception e) {
            onOrderFailure(clientOrderId, tradingPair, e);
        }
    }

    @Override
    public void cancel(String tradingPair, String clientOrderId) {
        InFlightOrder trackedOrder = orderTracker.findActiveOrder(clientOrderId, null).orElse(null);
        if (trackedOrder == null) {
            log.warn("orderId: {}에 해당하는 주문을 찾을 수 없습니다.", clientOrderId);
        }
        try {
            placeCancel(clientOrderId, trackedOrder);
            InFlightOrder.State newState = isCancelRequestInExchangeSynchronous ? InFlightOrder.State.CANCELED : InFlightOrder.State.PENDING_CANCEL;
            OrderUpdate orderUpdate = new OrderUpdate(tradingPair, getCurrentTimestamp(), newState, clientOrderId, null, null);
            orderTracker.processOrderUpdate(orderUpdate);
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
                lastPollTimestamp = getCurrentTimestamp();
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
        OrderUpdate orderUpdate = new OrderUpdate(order.getTradingPair(), placedOrder.timestamp(), InFlightOrder.State.OPEN, order.getClientOrderId(), placedOrder.exchangeOrderId());
        orderTracker.processOrderUpdate(orderUpdate);
        return placedOrder.exchangeOrderId();
    }

    /**
     * 동기적으로 주문 처리
     */
    protected abstract OrderPlacedDto placeOrder(String orderId, String tradingPair, BigDecimal amount, TradeType tradeType, OrderType orderType, BigDecimal price, Object... args);

    private void updateOrderAfterFailure(String orderId, String tradingPair, Exception exception) {
        OrderUpdate.OrderFailure failure = new OrderUpdate.OrderFailure(exception.getClass().getSimpleName(), exception.getMessage());
        OrderUpdate orderUpdate = new OrderUpdate(tradingPair, getCurrentTimestamp(), InFlightOrder.State.FAILED, orderId, null, failure);
        orderTracker.processOrderUpdate(orderUpdate);
    }

    private void onOrderFailure(String orderId, String tradingPair, Exception e) {
        log.error("{}에 대한 주문을 제출하는데 실패 했습니다, 네트워크 에러나 거래소 서버 상태, apiKey 문제 일 수 있습니다.", orderId);
        updateOrderAfterFailure(orderId, tradingPair, e);
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
        listenUserStream();
    }

    /**
     * 상태 폴링
     */
    protected void pollStatus() {
        CompletableFuture<Void> balancesFuture = CompletableFuture.runAsync(this::updateBalances, executor);
        CompletableFuture<Void> ordersFuture = CompletableFuture.runAsync(() -> {
            updateOrdersFills(); //order의 trades update
            updateOrders();  //order의 state update
        }, executor);
        CompletableFuture.allOf(balancesFuture, ordersFuture).join();
    }

    private void listenUserStream() {
        userStreamFuture = executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    JsonNode event = userStreamTracker.userStream.take();
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
                        .params(Map.of("symbols", tradingPairSymbolRegistry.getAllExchangeSymbols()))
                        .build()
        );
        this.tradingRules = parseTradingRule(body).stream().collect(Collectors.toMap(TradingRule::tradingPair, Function.identity()));
    }

    /**
     * using restApi
     */
    protected abstract void updateBalances();

    /**
     * using restApi
     */
    protected abstract List<TradeUpdate> fetchAllTradeUpdatesForOrder(InFlightOrder order);

    private void updateOrders() {
        List<InFlightOrder> orders = orderTracker.getActiveOrders().values().stream().toList();
        updateOrdersWithErrorHandler(orders, this::handleUpdateErrorForActiveOrder);
    }

    @FunctionalInterface
    private interface OrderErrorHandler {
        void handle(InFlightOrder order, Exception error);
    }

    private void updateOrdersWithErrorHandler(List<InFlightOrder> orders, OrderErrorHandler errorHandler) {
        executeParallel(orders, order -> {
            try {
                OrderUpdate update = fetchOrderStatus(order);
                orderTracker.processOrderUpdate(update);
            } catch (Exception e) {
                errorHandler.handle(order, e);
            }
        });
    }

    private void handleUpdateErrorForActiveOrder(InFlightOrder order, Exception error) {
        log.warn("Error fetching status update for active order(client order id: {}", order.getClientOrderId(), error);
        orderTracker.processOrderNotFound(order.getClientOrderId());
    }

    /**
     * using restApi
     * order의 상태를 조회한다.
     */
    protected abstract OrderUpdate fetchOrderStatus(InFlightOrder order);

    private void updateOrdersFills() {
        executeParallel(orderTracker.getFillableOrders().values(), order -> {
            List<TradeUpdate> tradeUpdates = fetchAllTradeUpdatesForOrder(order);
            tradeUpdates.forEach(orderTracker::processTradeUpdate);
        });
    }

    protected abstract void processUserStreamEvent(Object event);
    protected abstract List<TradingRule> parseTradingRule(JsonNode node);

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
            restAssistant.executeRequestAndGetResponse(
                    RestRequest.builder()
                            .method(HttpMethod.GET)
                            .pathUrl(checkNetworkRequestPath)
                            .build()
            );
            return NetworkStatus.CONNECTED;
        } catch (Exception e) {
            log.warn("network check failed", e);
            return NetworkStatus.NOT_CONNECTED;
        }
    }

    private void executeParallel(Collection<InFlightOrder> orders, Consumer<InFlightOrder> task) {
        List<Future<?>> futures = new ArrayList<>();
        for (InFlightOrder order : orders) {
            futures.add(executor.submit(() -> task.accept(order)));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // task 내부에서 처리
            }
        }
    }
}
