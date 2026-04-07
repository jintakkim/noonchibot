package com.hotak.noonchibot.core.order;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.EventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.AsyncTaskExecutor;

import java.time.Instant;
import java.util.*;

@Slf4j
public class OrderTracker implements SmartLifecycle {
    public static final int LOST_ORDER_COUNT_LIMIT = 2;
    private static final String LOST_ERROR_TYPE = "lost";

    private final ExchangeEventPublisher eventPublisher;
    private final String platformName;
    private final TradeRepository tradeRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final AsyncTaskExecutor ioExecutor;
    private final ExchangeEventSubscriber eventSubscriber;
    private final Map<String, InFlightOrder> inFlightOrders = new HashMap<>();
    private final Map<String, Integer> orderNotFoundRecords = new HashMap<>();
    private volatile boolean running = false;

    private final EventListener<OrderRequestSentEvent> orderRequestEventListener;
    private final EventListener<OrderUpdateEvent> orderUpdateEventListener;
    private final EventListener<TradeUpdateEvent> tradeUpdateEventListener;
    private final EventListener<OrderLostEvent> orderLostEventListener;

    public OrderTracker(
            ExchangeEventPublisher eventPublisher,
            String platformName,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository,
            MainExecutor mainExecutor,
            AsyncTaskExecutor ioExecutor,
            ExchangeEventSubscriber eventSubscriber
    ) {
        this.eventPublisher = eventPublisher;
        this.platformName = platformName;
        this.tradeRepository = tradeRepository;
        this.orderHistoryRepository = orderHistoryRepository;
        this.ioExecutor = ioExecutor;
        this.eventSubscriber = eventSubscriber;

        this.orderRequestEventListener = event -> mainExecutor.execute(() -> startTrackingOrder(event.inFlightOrder()));
        this.orderUpdateEventListener = event -> mainExecutor.execute(() -> processOrderUpdate(event));
        this.tradeUpdateEventListener = event -> mainExecutor.execute(() -> processTradeUpdate(event));
        this.orderLostEventListener = event -> mainExecutor.execute(() -> processOrderNotFound(event.clientOrderId()));
    }

    @VisibleForTesting
    void startTrackingOrder(InFlightOrder inFlightOrder) {
        inFlightOrders.put(inFlightOrder.getClientOrderId(), inFlightOrder);
    }

    @VisibleForTesting
    void processTradeUpdate(TradeUpdateEvent tradeUpdateEvent) {
        if (tradeUpdateEvent.clientOrderId() == null && tradeUpdateEvent.exchangeOrderId() == null) throw new IllegalArgumentException("TradeUpdate에 오더 아이디 정보가 없습니다.");

        InFlightOrder trackedInFlightOrder = getInFlightOrderById(tradeUpdateEvent.clientOrderId(), tradeUpdateEvent.exchangeOrderId());
        if (trackedInFlightOrder == null) {
            log.error("can't find tracked inFlightOrder (client inFlightOrder id: {}, exchange inFlightOrder id: {})", tradeUpdateEvent.clientOrderId(), tradeUpdateEvent.exchangeOrderId());
            return;
        }
        trackedInFlightOrder.updateWithTradeUpdate(tradeUpdateEvent);
        ioExecutor.execute(() -> tradeRepository.save(tradeUpdateEvent.toTrade(platformName)));
        triggerOrderFilledEvent(trackedInFlightOrder, tradeUpdateEvent);
    }

    @VisibleForTesting
    void processOrderUpdate(OrderUpdateEvent orderUpdateEvent) {
        if (orderUpdateEvent.clientOrderId() == null && orderUpdateEvent.exchangeOrderId() == null) throw new IllegalArgumentException("OrderUpdate에 오더 아이디 정보가 없습니다.");
        InFlightOrder inFlightOrder = getInFlightOrderById(orderUpdateEvent.clientOrderId(), orderUpdateEvent.exchangeOrderId());
        if(inFlightOrder == null) {
            log.warn("트레킹되지 않은 오더의 업데이트 이벤트가 발생했습니다.");
            return;
        }
        OrderState prevState = inFlightOrder.getCurrentState();
        inFlightOrder.updateWithOrderUpdate(orderUpdateEvent);
        if(isOrderCreation(prevState, inFlightOrder)) triggerCreatedEvent(inFlightOrder);
        if(inFlightOrder.isDone()) {
            inFlightOrders.remove(inFlightOrder.getClientOrderId());
            orderNotFoundRecords.remove(inFlightOrder.getClientOrderId());
            OrderState currentState = inFlightOrder.getCurrentState();
            if (currentState == OrderState.CANCELED) {
                triggerCanceledEvent(inFlightOrder);
            }
            if(currentState == OrderState.FILLED) {
                triggerCompletedEvent(inFlightOrder);
            }
            if(currentState == OrderState.FAILED) {
                triggerFailureEvent(inFlightOrder, orderUpdateEvent);
            }
            ioExecutor.execute(() -> {
                boolean isLostUpdate = isOrderLostUpdate(orderUpdateEvent);
                orderHistoryRepository.save(OrderHistory.from(inFlightOrder, isLostUpdate, platformName));
            });
        }
    }

    private boolean isOrderCreation(OrderState prevState, InFlightOrder inFlightOrder) {
        return prevState == OrderState.PENDING_CREATE && inFlightOrder.getCurrentState() == OrderState.OPEN;
    }

    @VisibleForTesting
    void processOrderNotFound(String clientOrderId) {
        InFlightOrder inFlightOrder = getInFlightOrderByClientId(clientOrderId);
        if(inFlightOrder == null) return; // ignore
        log.warn("주문 (clientId: {} exchangeId: {})을 소실된 주문으로 처리합니다.", clientOrderId, inFlightOrder.getExchangeOrderId());
        orderNotFoundRecords.merge(clientOrderId, 1, Integer::sum);
        if (orderNotFoundRecords.get(clientOrderId) > LOST_ORDER_COUNT_LIMIT && !inFlightOrder.getCurrentState().isTerminal()) {
            processOrderUpdate(new OrderUpdateEvent(
                    inFlightOrder.getTradingPair(),
                    Instant.now(),
                    OrderState.FAILED,
                    clientOrderId,
                    inFlightOrder.getExchangeOrderId(),
                    new OrderUpdateEvent.OrderFailure(LOST_ERROR_TYPE, "order lost")
            ));
        }
    }

    private void triggerCreatedEvent(InFlightOrder inFlightOrder) {
        if (inFlightOrder.getTradeType() == TradeType.BUY) {
            BuyOrderCreatedEvent event = new BuyOrderCreatedEvent(
                    inFlightOrder.getLastUpdateTimestamp(), inFlightOrder.getOrderType(), inFlightOrder.getTradingPair(),
                    inFlightOrder.getAmount(), inFlightOrder.getPrice(), inFlightOrder.getClientOrderId(),
                    inFlightOrder.getCreationTimestamp(), inFlightOrder.getExchangeOrderId()
            );
            eventPublisher.publish(event);

        } else {
            SellOrderCreatedEvent event = new SellOrderCreatedEvent(
                    inFlightOrder.getLastUpdateTimestamp(), inFlightOrder.getOrderType(), inFlightOrder.getTradingPair(),
                    inFlightOrder.getAmount(), inFlightOrder.getPrice(), inFlightOrder.getClientOrderId(),
                    inFlightOrder.getCreationTimestamp(), inFlightOrder.getExchangeOrderId()
            );
            eventPublisher.publish(event);
        }
    }

    private void triggerCanceledEvent(InFlightOrder inFlightOrder) {
        eventPublisher.publish(new OrderCanceledEvent(inFlightOrder.getLastUpdateTimestamp(), inFlightOrder.getClientOrderId(), inFlightOrder.getExchangeOrderId()));
    }

    private void triggerOrderFilledEvent(InFlightOrder inFlightOrder, TradeUpdateEvent tradeUpdateEvent) {
        eventPublisher.publish(
                new OrderFilledEvent(
                        inFlightOrder.getLastUpdateTimestamp(), inFlightOrder.getClientOrderId(), inFlightOrder.getTradingPair(),
                        inFlightOrder.getTradeType(), inFlightOrder.getOrderType(), tradeUpdateEvent.fillBaseAmount(), tradeUpdateEvent.fillPrice(),
                        tradeUpdateEvent.fee(), tradeUpdateEvent.tradeId(), tradeUpdateEvent.exchangeOrderId()
                )
        );
    }

    private void triggerCompletedEvent(InFlightOrder inFlightOrder) {
        if (inFlightOrder.getTradeType() == TradeType.BUY) {
            BuyOrderCompletedEvent event = new BuyOrderCompletedEvent(
                    inFlightOrder.getLastUpdateTimestamp(), inFlightOrder.getOrderType(), inFlightOrder.getTradingPair(),
                    inFlightOrder.getAmount(), inFlightOrder.getPrice(), inFlightOrder.getClientOrderId(),
                    inFlightOrder.getCreationTimestamp(), inFlightOrder.getExchangeOrderId()
            );
            eventPublisher.publish(event);

        } else {
            SellOrderCompletedEvent event = new SellOrderCompletedEvent(
                    inFlightOrder.getLastUpdateTimestamp(), inFlightOrder.getOrderType(), inFlightOrder.getTradingPair(),
                    inFlightOrder.getAmount(), inFlightOrder.getPrice(), inFlightOrder.getClientOrderId(),
                    inFlightOrder.getCreationTimestamp(), inFlightOrder.getExchangeOrderId()
            );
            eventPublisher.publish(event);
        }
    }

    private void triggerFailureEvent(InFlightOrder inFlightOrder, OrderUpdateEvent orderUpdateEvent) {
        OrderFailureEvent event = new OrderFailureEvent(
                inFlightOrder.getLastUpdateTimestamp(),
                inFlightOrder.getClientOrderId(),
                inFlightOrder.getOrderType(),
                orderUpdateEvent.orderFailure()
        );
        eventPublisher.publish(event);
    }

    public InFlightOrder getInFlightOrderById(String clientOrderId, String exchangeOrderId) {
        if(clientOrderId != null) {
            InFlightOrder order = inFlightOrders.get(clientOrderId);
            if(order != null) return order;
        }
        if(exchangeOrderId != null) {
            return inFlightOrders.values().stream()
                    .filter(order -> {
                        if(order.getExchangeOrderId() == null ) return false;
                        return order.getExchangeOrderId().equals(exchangeOrderId);
                    }).findAny().orElse(null);
        }
        return null;
    }

    public InFlightOrder getInFlightOrderByClientId(String clientOrderId) {
        return getInFlightOrderById(clientOrderId, null);
    }

    public InFlightOrder getInFlightOrderByExchangeId(String exchangeOrderId) {
        return getInFlightOrderById(null, exchangeOrderId);
    }

    private boolean isOrderLostUpdate(OrderUpdateEvent event) {
        if(event.orderFailure() == null) return false;
        return event.orderFailure().errorType().equals(LOST_ERROR_TYPE);
    }

    @Override
    public void start() {
        eventSubscriber.subscribe(OrderRequestSentEvent.class, orderRequestEventListener);
        eventSubscriber.subscribe(OrderUpdateEvent.class, orderUpdateEventListener);
        eventSubscriber.subscribe(TradeUpdateEvent.class, tradeUpdateEventListener);
        eventSubscriber.subscribe(OrderLostEvent.class, orderLostEventListener);
        running = true;
    }

    @Override
    public void stop() {
        eventSubscriber.unsubscribe(OrderRequestSentEvent.class, orderRequestEventListener);
        eventSubscriber.unsubscribe(OrderUpdateEvent.class, orderUpdateEventListener);
        eventSubscriber.unsubscribe(TradeUpdateEvent.class, tradeUpdateEventListener);
        eventSubscriber.unsubscribe(OrderLostEvent.class, orderLostEventListener);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public Collection<InFlightOrder> getAll() {
        return inFlightOrders.values();
    }
}