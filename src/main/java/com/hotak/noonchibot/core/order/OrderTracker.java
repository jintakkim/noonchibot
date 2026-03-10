package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class OrderTracker {
    private static final int LOST_ORDER_COUNT_LIMIT = 2;

    private final ExchangeEventPublisher eventPublisher;
    private final ExchangeEventSubscriber eventSubscriber;

    private final Map<String, InFlightOrder> activeOrders = new ConcurrentHashMap<>();
    private final Map<String, InFlightOrder> lostOrders = new ConcurrentHashMap<>();
    private final Map<String, Integer> orderNotFoundRecords = new ConcurrentHashMap<>();

    public OrderTracker(ExchangeEventPublisher eventPublisher, ExchangeEventSubscriber eventSubscriber) {
        this.eventPublisher = eventPublisher;
        this.eventSubscriber = eventSubscriber;
        subscribeEvents();
    }

    private void subscribeEvents() {
        eventSubscriber.subscribe(OrderRequestSentEvent.class, event -> startTrackingOrder(event.inFlightOrder()));
        eventSubscriber.subscribe(OrderUpdateEvent.class, this::processOrderUpdate);
        eventSubscriber.subscribe(TradeUpdateEvent.class, this::processTradeUpdate);
    }

    /**
     * fillable means active + lost orders
     */
    public Map<String, InFlightOrder> getFillableOrders() {
        Map<String, InFlightOrder> fillable = new HashMap<>(activeOrders);
        fillable.putAll(lostOrders);
        return fillable;
    }

    public Map<String, InFlightOrder> getActiveOrders() {
        return new HashMap<>(activeOrders);
    }

    public Map<String, InFlightOrder> getLostOrders() {
        return new HashMap<>(lostOrders);
    }

    public void startTrackingOrder(InFlightOrder order) {
        activeOrders.put(order.getClientOrderId(), order);
    }

    private void stopTrackingOrder(String clientOrderId) {
        InFlightOrder order = activeOrders.remove(clientOrderId);
        if (order != null) {
            orderNotFoundRecords.remove(clientOrderId);
        }
    }

    public Optional<InFlightOrder> findActiveOrder(String clientOrderId, String exchangeOrderId) {
        if(clientOrderId != null) return Optional.ofNullable(activeOrders.get(clientOrderId));
        if(exchangeOrderId != null) return findInFlightOrderByExchangeOrderId(activeOrders.values(), exchangeOrderId);
        return Optional.empty();
    }

    public Optional<InFlightOrder> findLostOrder(String clientOrderId, String exchangeOrderId) {
        if(clientOrderId != null) return Optional.ofNullable(lostOrders.get(clientOrderId));
        if(exchangeOrderId != null) return findInFlightOrderByExchangeOrderId(lostOrders.values(), exchangeOrderId);
        return Optional.empty();
    }

    private Optional<InFlightOrder> findInFlightOrderByExchangeOrderId(Collection<InFlightOrder> inFlightOrders, String exchangeOrderId) {
        return inFlightOrders.stream()
                .filter(order -> exchangeOrderId.equals(order.getExchangeOrderId()))
                .findAny();
    }

    public void processTradeUpdate(TradeUpdateEvent tradeUpdateEvent) {
        InFlightOrder trackedOrder = getFillableOrders().get(tradeUpdateEvent.clientOrderId());
        if (trackedOrder == null) {
            log.error("can't find tracked order for client order id: {}", tradeUpdateEvent.clientOrderId());
            return;
        }
        trackedOrder.updateWithTradeUpdate(tradeUpdateEvent);
        triggerOrderFilledEvent(trackedOrder, tradeUpdateEvent);
    }

    public void processOrderUpdate(OrderUpdateEvent orderUpdateEvent) {
        if (orderUpdateEvent.clientOrderId() == null && orderUpdateEvent.exchangeOrderId() == null) {
            throw new IllegalArgumentException("OrderUpdate에 오더 아이디 정보가 없습니다.");
        }
        Optional<InFlightOrder> optTrackedOrder = findActiveOrder(orderUpdateEvent.clientOrderId(), orderUpdateEvent.exchangeOrderId());
        if(optTrackedOrder.isEmpty()) {
            handleLostOrder(orderUpdateEvent);
            return;
        }
        InFlightOrder order = optTrackedOrder.get();
        InFlightOrder.State prevState = order.getCurrentState();
        order.updateWithOrderUpdate(orderUpdateEvent);
        if(isOrderCreation(prevState, order)) triggerCreatedEvent(order);
        if(order.isDone()) {
            InFlightOrder.State currentState = order.getCurrentState();
            if (currentState == InFlightOrder.State.CANCELED) {
                triggerCanceledEvent(order);
            }
            if(currentState == InFlightOrder.State.FILLED) {
                triggerCompletedEvent(order);
            }
            if(currentState == InFlightOrder.State.FAILED) {
                triggerFailureEvent(order, orderUpdateEvent);
            }
            stopTrackingOrder(order.getClientOrderId());
        }
    }

    private boolean isOrderCreation(InFlightOrder.State prevState, InFlightOrder order) {
        return prevState == InFlightOrder.State.PENDING_CREATE && order.getCurrentState().isAcceptedByExchange();
    }

    public void processOrderNotFound(String clientOrderId) {
        InFlightOrder trackedOrder = activeOrders.get(clientOrderId);
        if (trackedOrder != null) {
            orderNotFoundRecords.merge(clientOrderId, 1, Integer::sum);
            if (orderNotFoundRecords.get(clientOrderId) > LOST_ORDER_COUNT_LIMIT && !trackedOrder.getCurrentState().isTerminal()) {
                log.warn("주문 {}({})을 소실된 주문으로 처리합니다.", clientOrderId, trackedOrder.getExchangeOrderId());
                processOrderUpdate(new OrderUpdateEvent(
                        trackedOrder.getTradingPair(),
                        Instant.now(),
                        InFlightOrder.State.FAILED,
                        clientOrderId,
                        null,
                        null
                ));
                lostOrders.put(clientOrderId, trackedOrder);
            }
            return;
        }
        // active에 없으면 lost에서 확인
        InFlightOrder lostOrder = lostOrders.get(clientOrderId);
        if (lostOrder != null) {
            log.info("소실된 주문 {}({})을 제거합니다.", clientOrderId, lostOrder.getExchangeOrderId());
            lostOrders.remove(clientOrderId);
        }
    }

    private void triggerCreatedEvent(InFlightOrder order) {
        if (order.getTradeType() == TradeType.BUY) {
            BuyOrderCreatedEvent event = new BuyOrderCreatedEvent(
                    order.getLastUpdateTimestamp(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.publish(event);

        } else {
            SellOrderCreatedEvent event = new SellOrderCreatedEvent(
                    order.getLastUpdateTimestamp(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.publish(event);
        }
    }

    private void triggerCanceledEvent(InFlightOrder order) {
        eventPublisher.publish(new OrderCanceledEvent(order.getLastUpdateTimestamp(), order.getClientOrderId(), order.getExchangeOrderId()));
    }

    private void triggerOrderFilledEvent(InFlightOrder order, TradeUpdateEvent tradeUpdateEvent) {
        eventPublisher.publish(
                new OrderFilledEvent(
                        order.getLastUpdateTimestamp(), order.getClientOrderId(), order.getTradingPair(),
                        order.getTradeType(), order.getOrderType(), tradeUpdateEvent.fillBaseAmount(), tradeUpdateEvent.fillPrice(),
                        tradeUpdateEvent.fee(), tradeUpdateEvent.tradeId(), tradeUpdateEvent.exchangeOrderId()
                )
        );
    }

    private void triggerCompletedEvent(InFlightOrder order) {
        if (order.getTradeType() == TradeType.BUY) {
            BuyOrderCompletedEvent event = new BuyOrderCompletedEvent(
                    order.getLastUpdateTimestamp(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.publish(event);

        } else {
            SellOrderCompletedEvent event = new SellOrderCompletedEvent(
                    order.getLastUpdateTimestamp(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.publish(event);
        }
    }

    private void triggerFailureEvent(InFlightOrder order, OrderUpdateEvent orderUpdateEvent) {
        OrderFailureEvent event = new OrderFailureEvent(
                order.getLastUpdateTimestamp(),
                order.getClientOrderId(),
                order.getOrderType(),
                orderUpdateEvent.orderFailure()
        );
        eventPublisher.publish(event);
    }


    /**
     * 네트워크 문제등의 이유로 트레킹에 실패한 오더가 뒤늦게 오는 경우 완전히 제거
     */
    private void handleLostOrder(OrderUpdateEvent orderUpdateEvent) {
        Optional<InFlightOrder> optLostOrder = findLostOrder(orderUpdateEvent.clientOrderId(), orderUpdateEvent.exchangeOrderId());
        if (optLostOrder.isEmpty()) return;
        InFlightOrder lostOrder = optLostOrder.get();
        InFlightOrder.State state = orderUpdateEvent.newState();
        if (state.isTerminal()) {
            lostOrders.remove(lostOrder.getClientOrderId());
            log.debug("종료된 lost order를 목록에서 제거했습니다: {}", lostOrder.getClientOrderId());
        }
    }

}