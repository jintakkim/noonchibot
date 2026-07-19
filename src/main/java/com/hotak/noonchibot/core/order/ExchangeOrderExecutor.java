package com.hotak.noonchibot.core.order;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.OrderIdGenerator;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.connector.web.ExchangeRejectedException;
import com.hotak.noonchibot.connector.web.ExchangeTransientException;
import com.hotak.noonchibot.connector.web.RequestNotExecutedException;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.api.OrderCommandApi;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.trade.TradingRule;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ExchangeOrderExecutor implements LifecycleAware, OrderCommandApi {
    private final OrderIdGenerator orderIdGenerator;
    private final OrderTracker orderTracker;
    private final TradingRuleRegistry tradingRuleRegistry;
    private final String clientOrderIdPrefix;
    private final int clientOrderIdMaxLength;
    private final Set<TimeInForce> supportedTimeInForce;
    private final OrderBookTracker orderBookTracker;
    private final EventPublisher eventPublisher;
    private final OrderClient orderClient;
    private final EventSubscriber eventSubscriber;
    private final Exchange exchange;
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final Set<Subscription> subscriptions = new HashSet<>();

    public ExchangeOrderExecutor(
            OrderIdGenerator orderIdGenerator,
            OrderTracker orderTracker,
            TradingRuleRegistry tradingRuleRegistry,
            String clientOrderIdPrefix,
            int clientOrderIdMaxLength,
            Set<TimeInForce> supportedTimeInForce,
            OrderBookTracker orderBookTracker,
            EventPublisher eventPublisher,
            OrderClient orderClient,
            EventSubscriber eventSubscriber,
            Exchange exchange,
            OrderSnapshotRepository orderSnapshotRepository
    ) {
        this.orderIdGenerator = orderIdGenerator;
        this.orderTracker = orderTracker;
        this.tradingRuleRegistry = tradingRuleRegistry;
        this.clientOrderIdPrefix = clientOrderIdPrefix;
        this.clientOrderIdMaxLength = clientOrderIdMaxLength;
        this.supportedTimeInForce = supportedTimeInForce;
        this.orderBookTracker = orderBookTracker;
        this.eventPublisher = eventPublisher;
        this.orderClient = orderClient;
        this.eventSubscriber = eventSubscriber;
        this.exchange = exchange;
        this.orderSnapshotRepository = orderSnapshotRepository;
    }

    public String createClientOrderId(boolean isBuy, String tradingPair) {
        return orderIdGenerator.createClientOrderId(
                isBuy,
                tradingPair,
                clientOrderIdPrefix,
                clientOrderIdMaxLength
        );
    }

    @Override
    public void onStart() {
        subscriptions.add(eventSubscriber.subscribe(
                OrderEvent.ExchangeCreateRequested.class,
                this::processExchangeCreateRequest,
                ExecutionPolicy.concurrent()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                OrderEvent.ExchangeCancelRequested.class,
                this::processExchangeCancelRequest,
                ExecutionPolicy.concurrent()
        ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public int phase() {
        return Phases.ORDER_EXECUTOR_SETUP;
    }

    @Override
    public String createOrder(OrderCandidate candidate) {
        boolean isBuy = candidate.getTradeType() == TradeType.BUY;
        String clientOrderId = createClientOrderId(isBuy, candidate.getTradingPair());
        InFlightOrder order = createValidatedInFlightOrder(candidate, clientOrderId);
        orderTracker.startTrackingOrder(order);
        eventPublisher.publish(new OrderEvent.ExchangeCreateRequested(order));
        return clientOrderId;
    }

    private InFlightOrder createValidatedInFlightOrder(OrderCandidate candidate, String clientOrderId) {
        TradingRule tradingRule = findTradingRuleOrElseThrow(candidate.getTradingPair());

        BigDecimal quantizedPrice = quantizeOrderPrice(candidate);
        BigDecimal quantizedAmount = quantizeOrderAmount(candidate.getTradingPair(), candidate.getAmount());
        validateOrderType(clientOrderId, candidate, tradingRule);
        validateTimeInForce(clientOrderId, candidate);
        validateOrderSize(clientOrderId, candidate, quantizedAmount, tradingRule);
        validateNotionalSize(clientOrderId, candidate, quantizedPrice, quantizedAmount, tradingRule);

        return new InFlightOrder(
                clientOrderId,
                candidate.getTradingPair(),
                candidate.getOrderType(),
                candidate.getTradeType(),
                quantizedAmount,
                quantizedPrice,
                Instant.now(),
                candidate.isPostOnly(),
                candidate.getTimeInForce()
        );
    }

    private BigDecimal quantizeOrderPrice(OrderCandidate candidate) {
        if (candidate.getOrderType() != OrderType.LIMIT) {
            return candidate.getPrice();
        }
        return quantizePrice(candidate.getTradingPair(), candidate.getPrice());
    }

    private BigDecimal quantizePrice(String tradingPair, BigDecimal price) {
        if (price == null) {
            return null;
        }
        BigDecimal priceQuantum = findTradingRuleOrElseThrow(tradingPair).minPriceIncrement();
        return price.divideToIntegralValue(priceQuantum).multiply(priceQuantum);
    }

    private BigDecimal quantizeOrderAmount(String tradingPair, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal sizeQuantum = findTradingRuleOrElseThrow(tradingPair).minBaseAmountIncrement();
        return amount.divideToIntegralValue(sizeQuantum).multiply(sizeQuantum);
    }

    private void validateOrderType(String clientOrderId, OrderCandidate candidate, TradingRule tradingRule) {
        if (!tradingRule.supportedOrderTypes().contains(candidate.getOrderType())) {
            throw new OrderValidationException.UnsupportedOrderTypeException(
                    clientOrderId,
                    candidate.getTradingPair(),
                    "해당 오더 타입은 지원하지 않습니다."
            );
        }
    }

    private void validateTimeInForce(String clientOrderId, OrderCandidate candidate) {
        if (!supportedTimeInForce.contains(candidate.getTimeInForce())) {
            throw new OrderValidationException.UnsupportedTimeInForceException(
                    clientOrderId,
                    candidate.getTradingPair(),
                    "해당 timeInForce는 지원하지 않습니다."
            );
        }
    }

    private void validateOrderSize(
            String clientOrderId,
            OrderCandidate candidate,
            BigDecimal quantizedAmount,
            TradingRule tradingRule
    ) {
        if (quantizedAmount.compareTo(tradingRule.minOrderSize()) < 0) {
            throw new OrderValidationException.BelowMinOrderSizeException(
                    clientOrderId,
                    candidate.getTradingPair(),
                    "주문 수량이 최소 주문 수량보다 커야합니다."
            );
        }
    }

    private void validateNotionalSize(
            String clientOrderId,
            OrderCandidate candidate,
            BigDecimal quantizedPrice,
            BigDecimal quantizedAmount,
            TradingRule tradingRule
    ) {
        BigDecimal estimatedPrice = estimatedPrice(candidate, quantizedPrice);
        BigDecimal notionalSize = estimatedPrice.multiply(quantizedAmount);
        if (notionalSize.compareTo(tradingRule.minNotionalSize()) < 0) {
            throw new OrderValidationException.BelowMinNotionalException(
                    clientOrderId,
                    candidate.getTradingPair(),
                    "주문 금액이 최소 주문 금액보다 커야합니다."
            );
        }
    }

    private BigDecimal estimatedPrice(OrderCandidate candidate, BigDecimal quantizedPrice) {
        if (candidate.getPrice() != null) {
            return quantizedPrice;
        }
        OrderBook book = orderBookTracker.findOrderBook(candidate.getTradingPair())
                .orElseThrow(() -> new IllegalArgumentException("order book not found" + candidate.getTradingPair()));
        return candidate.getTradeType() == TradeType.BUY ? book.getBestAsk() : book.getBestBid();
    }

    private TradingRule findTradingRuleOrElseThrow(String tradingPair) {
        TradingRule rule = tradingRuleRegistry.getTradingRule(tradingPair);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
        }
        return rule;
    }

    @VisibleForTesting
    void processExchangeCreateRequest(OrderEvent.ExchangeCreateRequested event) {
        InFlightOrder order = event.inFlightOrder();
        orderSnapshotRepository.save(OrderSnapshot.from(exchange, order.toView()));
        try {
            OrderPlaceSuccess result = orderClient.placeOrder(order);
            publishStatus(order, result.exchangeOrderId(), result.orderState(), result.timestamp());
        } catch (ExchangeRejectedException cause) {
            publishStatus(order, null, OrderState.REJECTED, Instant.now());
            log.error(
                    "Exchange rejected order creation. tradingPair={}, clientOrderId={}",
                    order.getTradingPair(),
                    order.getClientOrderId(),
                    cause
            );
        } catch (RequestNotExecutedException cause) {
            // 거래소에 주문이 생성되지 않은 것이 확정되므로 생성 시도를 종결한다.
            publishStatus(order, null, OrderState.FAILED, Instant.now());
            log.error(
                    "Exchange did not execute order creation. tradingPair={}, clientOrderId={}",
                    order.getTradingPair(),
                    order.getClientOrderId(),
                    cause
            );
        } catch (ExchangeTransientException cause) {

            log.error(
                    "Order creation result is unknown. tradingPair={}, clientOrderId={}",
                    order.getTradingPair(),
                    order.getClientOrderId(),
                    cause
            );
        } catch (RuntimeException cause) {
            // 거래소 호출 중 발생한 예상하지 못한 오류도 처리 여부를 확정할 수 없다.
            log.error(
                    "Unexpected error while creating order. tradingPair={}, clientOrderId={}",
                    order.getTradingPair(),
                    order.getClientOrderId(),
                    cause
            );
        }
    }

    @Override
    public void placeCancel(String clientOrderId) {
        InFlightOrder order = orderTracker.getInFlightOrderByClientId(clientOrderId);
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다. clientOrderId: " + clientOrderId);
        }
        eventPublisher.publish(new OrderEvent.StatusReceived(
                order.getTradingPair(),
                order.getClientOrderId(),
                order.getExchangeOrderId(),
                OrderState.PENDING_CANCEL,
                Instant.now()
        ));
        eventPublisher.publish(new OrderEvent.ExchangeCancelRequested(
                order.getTradingPair(),
                order.getClientOrderId(),
                order.getExchangeOrderId()
        ));
    }

    @VisibleForTesting
    void processExchangeCancelRequest(OrderEvent.ExchangeCancelRequested event) {
        try {
            OrderCancelSuccess result = orderClient.cancelOrder(event.tradingPair(), event.clientOrderId());
            if (result.cancelFinalized()) {
                eventPublisher.publish(new OrderEvent.StatusReceived(
                        event.tradingPair(),
                        event.clientOrderId(),
                        event.exchangeOrderId(),
                        OrderState.CANCELED,
                        result.timestamp()
                ));
            }
        } catch (Exception cause) {
            // 이전 주문 상태가 보존되지 않으므로 실제 상태를 거래소에서 다시 확인한다.
            handleCancelFailure(event, cause);
        }
    }

    private void handleCancelFailure(OrderEvent.ExchangeCancelRequested event, Exception cause) {
        log.error(
                "Order cancellation failed; requesting current order status. "
                        + "tradingPair={}, clientOrderId={}, exchangeOrderId={}",
                event.tradingPair(),
                event.clientOrderId(),
                event.exchangeOrderId(),
                cause
        );
        requestStatusUpdate(event.tradingPair(), event.clientOrderId());
    }

    private void publishStatus(
            InFlightOrder order,
            String exchangeOrderId,
            OrderState state,
            Instant timestamp
    ) {
        eventPublisher.publish(new OrderEvent.StatusReceived(
                order.getTradingPair(),
                order.getClientOrderId(),
                exchangeOrderId,
                state,
                timestamp
        ));
    }

    private void requestStatusUpdate(String tradingPair, String clientOrderId) {
        eventPublisher.publish(new OrderEvent.StatusUpdateRequested(tradingPair, clientOrderId));
    }
}
