package com.hotak.noonchibot.core.order.executor;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderRequestSentEvent;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
public abstract class AbstractExchangeOrderExecutor implements OrderExecutor {
    private final String platformName;
    private final OrderIdGenerator orderIdGenerator;
    protected final OrderTracker orderTracker;
    protected final TradingRuleRegistry tradingRuleRegistry;
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
    private final ExchangeEventPublisher exchangeEventPublisher;


    public AbstractExchangeOrderExecutor(
            String platformName,
            OrderIdGenerator orderIdGenerator,
            OrderTracker orderTracker,
            TradingRuleRegistry tradingRuleRegistry,
            boolean isCancelRequestInExchangeSynchronous,
            String clientOrderIdPrefix,
            int clientOrderIdMaxLength,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookDataSource orderBookDataSource,
            ExchangeEventPublisher exchangeEventPublisher

    ) {
        this.platformName = platformName;
        this.orderIdGenerator = orderIdGenerator;
        this.orderTracker = orderTracker;
        this.tradingRuleRegistry = tradingRuleRegistry;
        this.isCancelRequestInExchangeSynchronous = isCancelRequestInExchangeSynchronous;
        this.clientOrderIdPrefix = clientOrderIdPrefix;
        this.clientOrderIdMaxLength = clientOrderIdMaxLength;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.orderBookDataSource = orderBookDataSource;
        this.exchangeEventPublisher = exchangeEventPublisher;
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
        TradingRule tradingRule = tradingRuleRegistry.getTradingRule(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");
        return tradingRule.minPriceIncrement();
    }

    @Override
    public BigDecimal getOrderSizeQuantum(String tradingPair, BigDecimal orderSize) {
        TradingRule tradingRule = tradingRuleRegistry.getTradingRule(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");
        return tradingRule.minBaseAmountIncrement();
    }

    @Override
    public List<String> getAllTradingPairs() {
        return tradingPairSymbolRegistry.getAllTradingPairs();
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
        TradingRule tradingRule = tradingRuleRegistry.getTradingRule(tradingPair);
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");

        BigDecimal quantizedPrice = price;
        if (orderType.equals(OrderType.LIMIT) || orderType.equals(OrderType.LIMIT_MAKER)) {
            quantizedPrice = quantizeOrderPrice(tradingPair, price);
        }
        BigDecimal quantizedOrderAmount = quantizeOrderAmount(tradingPair, amount);

        InFlightOrder order = new InFlightOrder(clientOrderId, tradingPair, orderType, tradeType, amount, price, Instant.now());
        exchangeEventPublisher.publish(new OrderRequestSentEvent(order));

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
            return;
        }
        try {
            placeCancel(clientOrderId, trackedOrder);
            InFlightOrder.State newState = isCancelRequestInExchangeSynchronous ? InFlightOrder.State.CANCELED : InFlightOrder.State.PENDING_CANCEL;
            OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent(tradingPair, Instant.now(), newState, clientOrderId, null, null);
            log.info("주문 취소 요청이 완료되었습니다. | {} | orderId: {} | exchangeOrderId: {}",
                    tradingPair,
                    clientOrderId,
                    trackedOrder.getExchangeOrderId() != null ? trackedOrder.getExchangeOrderId() : "N/A"
            );
            exchangeEventPublisher.publish(orderUpdateEvent);
        } catch (Exception e) {
            if(isOrderNotFoundDuringCancellationException(e)) {
                log.warn("orderId: {}에 해당하는 주문을 찾을 수 없습니다.", clientOrderId);
                orderTracker.processOrderNotFound(clientOrderId);
                return;
            }
            log.error("주문을 취소하는데 실패 헀습니다", e);
        }
    }

    private void placeOrderAndProcessUpdate(InFlightOrder order, Object... args) {
        OrderPlacedDto placedOrder = placeOrder(order.getClientOrderId(), order.getTradingPair(), order.getAmount(), order.getTradeType(), order.getOrderType(), order.getPrice(), args);
        OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent(order.getTradingPair(), placedOrder.timestamp(), InFlightOrder.State.OPEN, order.getClientOrderId(), placedOrder.exchangeOrderId());
        log.info("주문이 성공적으로 생성되었습니다. | {} {} {} | 수량: {} | 가격: {} | orderId: {} | exchangeOrderId: {}",
                order.getTradeType(),
                order.getTradingPair(),
                order.getOrderType(),
                order.getAmount().toPlainString(),
                order.getPrice() != null ? order.getPrice().toPlainString() : "MARKET",
                order.getClientOrderId(),
                order.getExchangeOrderId()
        );
        exchangeEventPublisher.publish(orderUpdateEvent);
    }

    /**
     * 동기적으로 주문 처리
     */
    protected abstract OrderPlacedDto placeOrder(String orderId, String tradingPair, BigDecimal amount, TradeType tradeType, OrderType orderType, BigDecimal price, Object... args);

    private void updateOrderAfterFailure(String orderId, String tradingPair, Exception exception) {
        OrderUpdateEvent.OrderFailure failure = new OrderUpdateEvent.OrderFailure(exception.getClass().getSimpleName(), exception.getMessage());
        OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent(tradingPair, Instant.now(), InFlightOrder.State.FAILED, orderId, null, failure);
        log.error("주문에 실패했습니다", exception);
        exchangeEventPublisher.publish(orderUpdateEvent);
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
    public BigDecimal quantizeOrderPrice(String tradingPair, BigDecimal price) {
        if (price == null) {
            return null;
        }
        BigDecimal priceQuantum = getOrderPriceQuantum(tradingPair, price);
        // (price // quantum) * quantum
        return price.divideToIntegralValue(priceQuantum).multiply(priceQuantum);
    }

    @Override
    public BigDecimal quantizeOrderAmount(String tradingPair, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal sizeQuantum = getOrderSizeQuantum(tradingPair, amount);
        return amount.divideToIntegralValue(sizeQuantum).multiply(sizeQuantum);
    }

    @Override
    public String getPlatformName() {
        return platformName;
    }
}
