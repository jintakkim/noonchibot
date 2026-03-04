package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.PubSub;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class OrderTracker {
    private static final int TRADE_FILLS_WAIT_TIMEOUT = 5;
    private static final int LOST_ORDER_COUNT_LIMIT = 2;

    private final PubSub eventPublisher;

    private final Map<String, InFlightOrder> activeOrders = new ConcurrentHashMap<>();
    private final Map<String, InFlightOrder> lostOrders = new ConcurrentHashMap<>();
    private final Map<String, Integer> orderNotFoundRecords = new ConcurrentHashMap<>();

    public OrderTracker(PubSub eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * fillable means active + lost orders
     */
    public Map<String, InFlightOrder> getFillableOrders() {
        Map<String, InFlightOrder> fillable = new HashMap<>(activeOrders);
        fillable.putAll(lostOrders);
        return fillable;
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

    public void processTradeUpdate(TradeUpdate tradeUpdate) {
        InFlightOrder trackedOrder = getFillableOrders().get(tradeUpdate.clientOrderId());
        if (trackedOrder == null) {
            log.error("can't find tracked order for client order id: {}", tradeUpdate.clientOrderId());
            return;
        }
        trackedOrder.updateWithTradeUpdate(tradeUpdate);
        triggerOrderFilledEvent(trackedOrder, tradeUpdate);
    }

    public void processOrderUpdate(OrderUpdate orderUpdate) {
        if (orderUpdate.clientOrderId() == null && orderUpdate.exchangeOrderId() == null) {
            throw new IllegalArgumentException("OrderUpdate에 오더 아이디 정보가 없습니다.");
        }
        Optional<InFlightOrder> optTrackedOrder = findActiveOrder(orderUpdate.clientOrderId(), orderUpdate.exchangeOrderId());
        if(optTrackedOrder.isEmpty()) {
            handleLostOrder(orderUpdate);
            return;
        }
        InFlightOrder order = optTrackedOrder.get();
        InFlightOrder.State prevState = order.getCurrentState();
        order.updateWithOrderUpdate(orderUpdate);
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
                triggerFailureEvent(order, orderUpdate);;
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
                processOrderUpdate(new OrderUpdate(
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
            eventPublisher.triggerEvent(event);

        } else {
            SellOrderCreatedEvent event = new SellOrderCreatedEvent(
                    order.getLastUpdateTimestamp(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.triggerEvent(event);
        }
    }

    private void triggerCanceledEvent(InFlightOrder order) {
        eventPublisher.triggerEvent(new OrderCanceledEvent(order.getLastUpdateTimestamp(), order.getClientOrderId(), order.getExchangeOrderId()));
    }

    private void triggerOrderFilledEvent(InFlightOrder order, TradeUpdate tradeUpdate) {
        eventPublisher.triggerEvent(
                new OrderFilledEvent(
                        order.getLastUpdateTimestamp(), order.getClientOrderId(), order.getTradingPair(),
                        order.getTradeType(), order.getOrderType(), tradeUpdate.fillBaseAmount(), tradeUpdate.fillPrice(),
                        tradeUpdate.tradeFee(), tradeUpdate.tradeId(), tradeUpdate.exchangeOrderId()
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
            eventPublisher.triggerEvent(event);

        } else {
            SellOrderCompletedEvent event = new SellOrderCompletedEvent(
                    order.getLastUpdateTimestamp(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.triggerEvent(event);
        }
    }

    private void triggerFailureEvent(InFlightOrder order, OrderUpdate orderUpdate) {
        OrderFailureEvent event = new OrderFailureEvent(
                order.getLastUpdateTimestamp(),
                order.getClientOrderId(),
                order.getOrderType(),
                orderUpdate.orderFailure()
        );
        eventPublisher.triggerEvent(event);
    }


    /**
     * 네트워크 문제등의 이유로 트레킹에 실패한 오더가 뒤늦게 오는 경우 완전히 제거
     */
    private void handleLostOrder(OrderUpdate orderUpdate) {
        Optional<InFlightOrder> optLostOrder = findLostOrder(orderUpdate.clientOrderId(), orderUpdate.exchangeOrderId());
        if (optLostOrder.isEmpty()) return;
        InFlightOrder lostOrder = optLostOrder.get();
        InFlightOrder.State state = orderUpdate.newState();
        if (state.isTerminal()) {
            lostOrders.remove(lostOrder.getClientOrderId());
            log.debug("종료된 lost order를 목록에서 제거했습니다: {}", lostOrder.getClientOrderId());
        }
    }

}