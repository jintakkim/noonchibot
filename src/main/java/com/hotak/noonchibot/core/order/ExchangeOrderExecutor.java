package com.hotak.noonchibot.core.order;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.OrderIdGenerator;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
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
public class ExchangeOrderExecutor implements LifecycleAware {
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
                OrderEvent.CreateRequested.class,
                this::processCreateRequest,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                OrderEvent.ExchangeCreateRequested.class,
                this::processExchangeCreateRequest,
                ExecutionPolicy.concurrent()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                OrderEvent.CancelRequested.class,
                this::processCancelRequest,
                ExecutionPolicy.sequential()
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

    @VisibleForTesting
    void processCreateRequest(OrderEvent.CreateRequested event) {
        if (!canHandle(event.exchange())) return;
        String clientOrderId = event.clientOrderId();
        try {
            if (clientOrderId == null) {
                clientOrderId = createClientOrderId(
                        event.candidate().getTradeType() == TradeType.BUY,
                        event.candidate().getTradingPair()
                );
            }
            InFlightOrder order = createValidatedInFlightOrder(event, clientOrderId);
            orderTracker.startTrackingOrder(order);
            eventPublisher.publish(new OrderEvent.ExchangeCreateRequested(order));
        } catch (Exception cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    event.candidate().getTradingPair(),
                    clientOrderId,
                    null,
                    cause
            ));
        }
    }

    private boolean canHandle(Exchange requestedExchange) {
        return requestedExchange == null || requestedExchange == exchange;
    }

    private InFlightOrder createValidatedInFlightOrder(OrderEvent.CreateRequested event, String clientOrderId) {
        OrderCandidate candidate = event.candidate();
        TradingRule tradingRule = findTradingRuleOrElseThrow(candidate.getTradingPair());

        BigDecimal quantizedPrice = quantizeOrderPrice(candidate);
        BigDecimal quantizedAmount = quantizeOrderAmount(candidate.getTradingPair(), candidate.getAmount());
        validateOrderType(clientOrderId, event, tradingRule);
        validateTimeInForce(clientOrderId, event);
        validateOrderSize(clientOrderId, event, quantizedAmount, tradingRule);
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

    private void validateOrderType(String clientOrderId, OrderEvent.CreateRequested event, TradingRule tradingRule) {
        if (!tradingRule.supportedOrderTypes().contains(event.candidate().getOrderType())) {
            throw new OrderValidationException.UnsupportedOrderTypeException(
                    clientOrderId,
                    event.candidate().getTradingPair(),
                    "해당 오더 타입은 지원하지 않습니다."
            );
        }
    }

    private void validateTimeInForce(String clientOrderId, OrderEvent.CreateRequested event) {
        if (!supportedTimeInForce.contains(event.candidate().getTimeInForce())) {
            throw new OrderValidationException.UnsupportedTimeInForceException(
                    clientOrderId,
                    event.candidate().getTradingPair(),
                    "해당 timeInForce는 지원하지 않습니다."
            );
        }
    }

    private void validateOrderSize(
            String clientOrderId,
            OrderEvent.CreateRequested event,
            BigDecimal quantizedAmount,
            TradingRule tradingRule
    ) {
        if (quantizedAmount.compareTo(tradingRule.minOrderSize()) < 0) {
            throw new OrderValidationException.BelowMinOrderSizeException(
                    clientOrderId,
                    event.candidate().getTradingPair(),
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
        try {
            saveInitialSnapshot(event.inFlightOrder());
            OrderPlaceResult result = orderClient.placeOrder(event.inFlightOrder());
            eventPublisher.publish(new OrderEvent.StatusReceived(
                    event.inFlightOrder().getTradingPair(),
                    event.inFlightOrder().getClientOrderId(),
                    result.exchangeOrderId(),
                    result.orderState(),
                    result.timestamp()
            ));
        } catch (Exception cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    event.inFlightOrder().getTradingPair(),
                    event.inFlightOrder().getClientOrderId(),
                    event.inFlightOrder().getExchangeOrderId(),
                    cause
            ));
        }
    }

    private void saveInitialSnapshot(InFlightOrder order) {
        orderSnapshotRepository.save(OrderSnapshot.from(exchange, order.toView()));
    }

    @VisibleForTesting
    void processCancelRequest(OrderEvent.CancelRequested event) {
        if (!canHandle(event.exchange())) return;
        try {
            InFlightOrder order = orderTracker.getInFlightOrderByClientId(event.clientOrderId());
            if (order == null) {
                throw new IllegalArgumentException("주문을 찾을 수 없습니다. clientOrderId: " + event.clientOrderId());
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
        } catch (Exception cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    null,
                    event.clientOrderId(),
                    null,
                    cause
            ));
        }
    }

    @VisibleForTesting
    void processExchangeCancelRequest(OrderEvent.ExchangeCancelRequested event) {
        try {
            OrderCancelResult result = orderClient.cancelOrder(event.tradingPair(), event.clientOrderId());
            eventPublisher.publish(new OrderEvent.StatusReceived(
                    event.tradingPair(),
                    event.clientOrderId(),
                    event.exchangeOrderId(),
                    result.cancelFinalized() ? OrderState.CANCELED : OrderState.PENDING_CANCEL,
                    result.timestamp()
            ));
        } catch (Exception cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    event.tradingPair(),
                    event.clientOrderId(),
                    event.exchangeOrderId(),
                    cause
            ));
        }
    }
}
