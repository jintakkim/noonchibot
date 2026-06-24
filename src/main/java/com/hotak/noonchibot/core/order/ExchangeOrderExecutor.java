package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.trade.TradingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
public class ExchangeOrderExecutor implements OrderedLifecycleAware {
    private final OrderIdGenerator orderIdGenerator;
    /**
     * 해당 봇에서 넣은 주문이라는 구분을 하기 위한 prefix
     */
    private final String clientOrderIdPrefix;
    /**
     * 거래소에서 설정한 id 최대 길이
     */
    private final int clientOrderIdMaxLength;
    private final EventSubscriber eventSubscriber;

    private final CreateRequestHandler createRequestHandler;
    private final ExchangeCreateRequestHandler exchangeCreateRequestHandler;
    private final CancelRequestHandler cancelRequestHandler;
    private final ExchangeCancelRequestHandler exchangeCancelRequestHandler;

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
        this.clientOrderIdPrefix = clientOrderIdPrefix;
        this.clientOrderIdMaxLength = clientOrderIdMaxLength;
        this.eventSubscriber = eventSubscriber;
        this.createRequestHandler = new CreateRequestHandler(
                eventPublisher,
                supportedTimeInForce,
                orderBookTracker,
                tradingRuleRegistry,
                orderTracker
        );
        this.exchangeCreateRequestHandler = new ExchangeCreateRequestHandler(
                eventPublisher,
                orderClient,
                orderSnapshotRepository,
                exchange
        );
        this.cancelRequestHandler = new CancelRequestHandler(eventPublisher, orderTracker);
        this.exchangeCancelRequestHandler = new ExchangeCancelRequestHandler(eventPublisher, orderClient);
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
    public int phase() {
        return Phases.ORDER_EXECUTOR_SETUP;
    }

    @Override
    public void onStart() {
        subscriptions.add(eventSubscriber.subscribe(OrderEvent.CreateRequested.class, createRequestHandler, ExecutionPolicy.sequential()));
        subscriptions.add(eventSubscriber.subscribe(OrderEvent.ExchangeCreateRequested.class, exchangeCreateRequestHandler, ExecutionPolicy.concurrent()));
        subscriptions.add(eventSubscriber.subscribe(OrderEvent.CancelRequested.class, cancelRequestHandler, ExecutionPolicy.sequential()));
        subscriptions.add(eventSubscriber.subscribe(OrderEvent.ExchangeCancelRequested.class, exchangeCancelRequestHandler, ExecutionPolicy.concurrent()));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    /**
     * 검증 단계
     */
    @RequiredArgsConstructor
    static class CreateRequestHandler implements FailureAwareEventHandler<OrderEvent.CreateRequested> {
        private final EventPublisher eventPublisher;
        private final Set<TimeInForce> supportedTimeInForce;
        private final OrderBookTracker orderBookTracker;
        private final TradingRuleRegistry tradingRuleRegistry;
        private final OrderTracker orderTracker;

        @Override
        public void onEvent(OrderEvent.CreateRequested event) {
            OrderCandidate candidate = event.candidate();
            TradingRule tradingRule = findTradingRuleOrElseThrow(candidate.getTradingPair());
            BigDecimal quantizedPrice = candidate.getPrice();
            if (candidate.getOrderType().equals(OrderType.LIMIT)) {
                quantizedPrice = quantizeOrderPrice(candidate.getTradingPair(), candidate.getPrice());
            }
            BigDecimal quantizedOrderAmount = quantizeOrderAmount(candidate.getTradingPair(), candidate.getAmount());

            if (!tradingRule.supportedOrderTypes().contains(candidate.getOrderType())) {
                throw new OrderValidationException.UnsupportedOrderTypeException(
                        event.clientOrderId(),
                        candidate.getTradingPair(),
                        "해당 오더 타입은 지원하지 않습니다."
                );
            }
            if(!supportedTimeInForce.contains(candidate.getTimeInForce())) {
                throw new OrderValidationException.UnsupportedTimeInForceException(
                        event.clientOrderId(),
                        candidate.getTradingPair(),
                        "해당 timeInForce는 지원하지 않습니다."
                );
            }
            if (quantizedOrderAmount.compareTo(tradingRule.minOrderSize()) < 0) {
                throw new OrderValidationException.BelowMinOrderSizeException(
                        event.clientOrderId(),
                        candidate.getTradingPair(),
                        "주문 수량이 최소 주문 수량보다 커야합니다."
                );
            }

            BigDecimal estimatedPrice;
            if (candidate.getPrice() != null) {
                estimatedPrice = quantizedPrice;
            } else {
                // 시장가 매수 → bestAsk, 시장가 매도 → bestBid
                OrderBook book = orderBookTracker.findOrderBook(candidate.getTradingPair())
                        .orElseThrow(() -> new IllegalArgumentException("order book not found" + candidate.getTradingPair()));
                estimatedPrice = candidate.getTradeType() == TradeType.BUY ? book.getBestAsk() : book.getBestBid();
            }
            BigDecimal notionalSize = estimatedPrice.multiply(quantizedOrderAmount);
            if (notionalSize.compareTo(tradingRule.minNotionalSize()) < 0) {
                throw new OrderValidationException.BelowMinNotionalException(
                        event.clientOrderId(),
                        candidate.getTradingPair(),
                        "주문 금액이 최소 주문 금액보다 커야합니다."
                );
            }
            InFlightOrder inFlightOrder = new InFlightOrder(
                    event.clientOrderId(),
                    candidate.getTradingPair(),
                    candidate.getOrderType(),
                    candidate.getTradeType(),
                    quantizedOrderAmount,
                    quantizedPrice,
                    Instant.now(),
                    candidate.isPostOnly(),
                    candidate.getTimeInForce()
            );
            orderTracker.startTrackingOrder(inFlightOrder);
            eventPublisher.publish(new OrderEvent.ExchangeCreateRequested(inFlightOrder));
        }

        private BigDecimal quantizeOrderPrice(String tradingPair, BigDecimal price) {
            if (price == null) {
                return null;
            }
            BigDecimal priceQuantum = getOrderPriceQuantum(tradingPair, price);
            // (price // quantum) * quantum
            return price.divideToIntegralValue(priceQuantum).multiply(priceQuantum);
        }

        private BigDecimal quantizeOrderAmount(String tradingPair, BigDecimal amount) {
            if (amount == null) {
                return null;
            }
            BigDecimal sizeQuantum = getOrderSizeQuantum(tradingPair, amount);
            return amount.divideToIntegralValue(sizeQuantum).multiply(sizeQuantum);
        }

        private BigDecimal getOrderPriceQuantum(String tradingPair, BigDecimal price) {
            TradingRule tradingRule = tradingRuleRegistry.getTradingRule(tradingPair);
            if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");
            return tradingRule.minPriceIncrement();
        }

        private BigDecimal getOrderSizeQuantum(String tradingPair, BigDecimal orderSize) {
            TradingRule tradingRule = tradingRuleRegistry.getTradingRule(tradingPair);
            if (tradingRule == null) throw new IllegalArgumentException("trading rule not found");
            return tradingRule.minBaseAmountIncrement();
        }

        private TradingRule findTradingRuleOrElseThrow(String tradingPair) {
            TradingRule rule = tradingRuleRegistry.getTradingRule(tradingPair);
            if(rule == null) throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
            return rule;
        }

        @Override
        public void onFailure(OrderEvent.CreateRequested event, Throwable cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    event.candidate().getTradingPair(),
                    event.clientOrderId(),
                    null,
                    cause
            ));
        }
    }

    @RequiredArgsConstructor
    static class CancelRequestHandler implements FailureAwareEventHandler<OrderEvent.CancelRequested> {
        private final EventPublisher eventPublisher;
        private final OrderTracker orderTracker;

        @Override
        public void onEvent(OrderEvent.CancelRequested event) {
            InFlightOrder order = orderTracker.getInFlightOrderByClientId(event.clientOrderId());
            if (order == null) {
                throw new IllegalArgumentException("주문을 찾을 수 없습니다. clientOrderId: "+ event.clientOrderId());
            }
            eventPublisher.publish(new OrderEvent.ExchangeCreateRequested(order));
        }

        @Override
        public void onFailure(OrderEvent.CancelRequested event, Throwable cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    null,
                    event.clientOrderId(),
                    null,
                    cause
            ));
        }
    }

    /**
     * 거래소 제출 단계
     */
    @RequiredArgsConstructor
    static class ExchangeCreateRequestHandler implements FailureAwareEventHandler<OrderEvent.ExchangeCreateRequested> {
        private final EventPublisher eventPublisher;
        private final OrderClient orderClient;
        private final OrderSnapshotRepository orderSnapshotRepository;
        private final Exchange exchange;

        @Override
        public void onEvent(OrderEvent.ExchangeCreateRequested event) {
            OrderSnapshot snapshot = new OrderSnapshot(
                    event.inFlightOrder().getClientOrderId(),
                    exchange,
                    event.inFlightOrder().getTradingPair(),
                    event.inFlightOrder().getCurrentState(),
                    event.inFlightOrder().getExchangeOrderId(),
                    event.inFlightOrder().getCreationTimestamp(),
                    event.inFlightOrder().getLastUpdateTimestamp()
            );
            orderSnapshotRepository.save(snapshot);
            OrderPlaceResult result = orderClient.placeOrder(event.inFlightOrder());
            eventPublisher.publish(new OrderEvent.StatusReceived(
                    event.inFlightOrder().getTradingPair(),
                    event.inFlightOrder().getClientOrderId(),
                    result.exchangeOrderId(),
                    result.orderState(),
                    result.timestamp()
            ));
        }

        @Override
        public void onFailure(OrderEvent.ExchangeCreateRequested event, Throwable cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    event.inFlightOrder().getTradingPair(),
                    event.inFlightOrder().getClientOrderId(),
                    event.inFlightOrder().getExchangeOrderId(),
                    cause
            ));
        }
    }

    /**
     * 거래소 제출 단계
     */
    @RequiredArgsConstructor
    static class ExchangeCancelRequestHandler implements FailureAwareEventHandler<OrderEvent.ExchangeCancelRequested> {
        private final EventPublisher eventPublisher;
        private final OrderClient orderClient;

        @Override
        public void onEvent(OrderEvent.ExchangeCancelRequested event) {
            OrderCancelResult result = orderClient.cancelOrder(event.tradingPair(), event.clientOrderId());
            eventPublisher.publish(new OrderEvent.StatusReceived(
                    event.tradingPair(),
                    event.clientOrderId(),
                    event.exchangeOrderId(),
                    result.cancelFinalized() ? OrderState.CANCELED : OrderState.PENDING_CANCEL,
                    result.timestamp()
            ));
        }

        @Override
        public void onFailure(OrderEvent.ExchangeCancelRequested event, Throwable cause) {
            eventPublisher.publish(new OrderEvent.Failed(
                    event.tradingPair(),
                    event.clientOrderId(),
                    event.exchangeOrderId(),
                    cause
            ));
        }
    }
}
