package com.hotak.noonchibot.core.order.execute;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderLostEvent;
import com.hotak.noonchibot.core.event.OrderRequestSentEvent;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
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

    public abstract Set<OrderType> getSupportedOrderType(String tradingPair);

    public abstract Set<TimeInForce> getSupportedTimeInForce();

    /**
     * 요청 에러가 시간 동기화 문제인지 확인
     */
    protected abstract boolean isRequestExceptionRelatedToTimeSynchronizer(Exception e);

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
    public String buy(OrderCandidate candidate) {
        String clientOrderId = orderIdGenerator.createClientOrderId(true, candidate.getTradingPair(), clientOrderIdPrefix, clientOrderIdMaxLength);
        createOrder(candidate, clientOrderId);
        return clientOrderId;
    }

    @Override
    public String sell(OrderCandidate candidate) {
        String clientOrderId = orderIdGenerator.createClientOrderId(false, candidate.getTradingPair(), clientOrderIdPrefix, clientOrderIdMaxLength);
        createOrder(candidate, clientOrderId);
        return clientOrderId;
    }

    private void createOrder(OrderCandidate candidate, String clientOrderId) {
        TradingRule tradingRule = tradingRuleRegistry.getTradingRule(candidate.getTradingPair());
        if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");

        BigDecimal quantizedPrice = candidate.getPrice();
        if (candidate.getOrderType().equals(OrderType.LIMIT)) {
            quantizedPrice = quantizeOrderPrice(candidate.getTradingPair(), candidate.getPrice());
        }
        BigDecimal quantizedOrderAmount = quantizeOrderAmount(candidate.getTradingPair(), candidate.getAmount());

        InFlightOrder inFlightOrder = new InFlightOrder(
                clientOrderId,
                candidate.getTradingPair(),
                candidate.getOrderType(),
                candidate.getTradeType(),
                candidate.getAmount(),
                candidate.getPrice(),
                Instant.now(),
                candidate.isPostOnly(),
                candidate.getTimeInForce()
        );
        exchangeEventPublisher.publish(new OrderRequestSentEvent(inFlightOrder));

        if (!getSupportedOrderType(candidate.getTradingPair()).contains(candidate.getOrderType())) {
            updateOrderAfterFailure(clientOrderId, candidate.getTradingPair(), new OrderValidationException.UnsupportedOrderTypeException("해당 오더 타입은 지원하지 않습니다."));
            return;
        }

        if(!getSupportedTimeInForce().contains(candidate.getTimeInForce())) {
            updateOrderAfterFailure(clientOrderId, candidate.getTradingPair(), new OrderValidationException.UnsupportedTimeInForceException("해당 timeInForce는 지원하지 않습니다."));
            return;
        }

        if (quantizedOrderAmount.compareTo(tradingRule.minOrderSize()) < 0) {
            updateOrderAfterFailure(clientOrderId, candidate.getTradingPair(), new OrderValidationException.BelowMinOrderSizeException("주문 수량이 최소 주문 수량보다 커야합니다."));
            return;
        }

        BigDecimal notionalSize = candidate.getPrice() == null ? orderBookDataSource.getLastTradedPrice(candidate.getTradingPair()).multiply(quantizedOrderAmount) : quantizedPrice.multiply(quantizedOrderAmount);
        if (notionalSize.compareTo(tradingRule.minNotionalSize()) < 0) {
            updateOrderAfterFailure(clientOrderId, candidate.getTradingPair(), new OrderValidationException.BelowMinNotionalException("주문 금액이 최소 주문 금액보다 커야합니다."));
            return;
        }
        try {
            placeOrderAndProcessUpdate(inFlightOrder);
        } catch (Exception e) {
            onOrderFailure(clientOrderId, candidate.getTradingPair(), e);
        }
    }

    @Override
    public void cancel(String tradingPair, String clientOrderId) {
        InFlightOrder order = orderTracker.getInFlightOrderByClientId(clientOrderId);
        if (order == null) {
            log.warn("orderId: {}에 해당하는 주문을 찾을 수 없습니다.", clientOrderId);
            return;
        }
        try {
            placeCancel(clientOrderId, order);
            OrderState newState = isCancelRequestInExchangeSynchronous ? OrderState.CANCELED : OrderState.PENDING_CANCEL;
            OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent(tradingPair, Instant.now(), newState, clientOrderId, null, null);
            log.info("주문 취소 요청이 완료되었습니다. | {} | orderId: {} | exchangeOrderId: {}",
                    tradingPair,
                    clientOrderId,
                    order.getExchangeOrderId() != null ? order.getExchangeOrderId() : "N/A"
            );
            exchangeEventPublisher.publish(orderUpdateEvent);
        } catch (Exception e) {
            if(isOrderNotFoundDuringCancellationException(e)) {
                log.warn("orderId: {}에 해당하는 주문을 찾을 수 없습니다.", clientOrderId);
                exchangeEventPublisher.publish(new OrderLostEvent(clientOrderId));
                return;
            }
            throw e;
        }
    }

    private void placeOrderAndProcessUpdate(InFlightOrder inFlightOrder) {
        OrderPlacedDto placedOrder = placeOrder(inFlightOrder);
        OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent(inFlightOrder.getTradingPair(), placedOrder.timestamp(), OrderState.OPEN, inFlightOrder.getClientOrderId(), placedOrder.exchangeOrderId());
        log.info("주문이 성공적으로 생성되었습니다. | {} {} {} | 수량: {} | 가격: {} | orderId: {} | exchangeOrderId: {}",
                inFlightOrder.getTradeType(),
                inFlightOrder.getTradingPair(),
                inFlightOrder.getOrderType(),
                inFlightOrder.getAmount().toPlainString(),
                inFlightOrder.getPrice() != null ? inFlightOrder.getPrice().toPlainString() : "MARKET",
                inFlightOrder.getClientOrderId(),
                inFlightOrder.getExchangeOrderId()
        );
        exchangeEventPublisher.publish(orderUpdateEvent);
    }

    /**
     * 동기적으로 주문 처리
     */
    protected abstract OrderPlacedDto placeOrder(InFlightOrder inFlightOrder);

    private void updateOrderAfterFailure(String orderId, String tradingPair, Exception exception) {
        OrderUpdateEvent.OrderFailure failure = new OrderUpdateEvent.OrderFailure(exception.getClass().getSimpleName(), exception.getMessage());
        OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent(tradingPair, Instant.now(), OrderState.FAILED, orderId, null, failure);
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
    protected abstract boolean placeCancel(String orderId, InFlightOrder order);

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
