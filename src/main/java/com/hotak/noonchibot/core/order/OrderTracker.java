package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

@Slf4j
public class OrderTracker {
    private static final int LOST_ORDER_COUNT_LIMIT = 2;

    private final ExchangeEventPublisher eventPublisher;
    private final ExchangeEventSubscriber eventSubscriber;
    private final InFlightOrderRepository inFlightOrderRepository;
    private final TradeRepository tradeRepository;

    public OrderTracker(
            ExchangeEventPublisher eventPublisher,
            ExchangeEventSubscriber eventSubscriber,
            InFlightOrderRepository inFlightOrderRepository,
            TradeRepository tradeRepository
    ) {
        this.eventPublisher = eventPublisher;
        this.eventSubscriber = eventSubscriber;
        this.inFlightOrderRepository = inFlightOrderRepository;
        this.tradeRepository = tradeRepository;
        subscribeEvents();
    }

    private void subscribeEvents() {
        eventSubscriber.subscribe(OrderRequestSentEvent.class, event -> startTrackingOrder(event.inFlightOrder()));
        eventSubscriber.subscribe(OrderUpdateEvent.class, this::processOrderUpdate);
        eventSubscriber.subscribe(TradeUpdateEvent.class, this::processTradeUpdate);
    }

    public void startTrackingOrder(InFlightOrder inFlightOrder) {
        inFlightOrderRepository.save(inFlightOrder);
    }

    public void processTradeUpdate(TradeUpdateEvent tradeUpdateEvent) {
        if (tradeUpdateEvent.clientOrderId() == null && tradeUpdateEvent.exchangeOrderId() == null) throw new IllegalArgumentException("TradeUpdate에 오더 아이디 정보가 없습니다.");
        InFlightOrder trackedInFlightOrder = inFlightOrderRepository.findById(tradeUpdateEvent.clientOrderId(), tradeUpdateEvent.exchangeOrderId()).orElse(null);
        if (trackedInFlightOrder == null) {
            log.error("can't find tracked inFlightOrder (client inFlightOrder id: {}, exchange inFlightOrder id: {})", tradeUpdateEvent.clientOrderId(), tradeUpdateEvent.exchangeOrderId());
            return;
        }
        trackedInFlightOrder.updateWithTradeUpdate(tradeUpdateEvent);
        tradeRepository.save(tradeUpdateEvent.toTrade());
        triggerOrderFilledEvent(trackedInFlightOrder, tradeUpdateEvent);
    }

    public void processOrderUpdate(OrderUpdateEvent orderUpdateEvent) {
        if (orderUpdateEvent.clientOrderId() == null && orderUpdateEvent.exchangeOrderId() == null) throw new IllegalArgumentException("OrderUpdate에 오더 아이디 정보가 없습니다.");
        InFlightOrder inFlightOrder = inFlightOrderRepository.findById(orderUpdateEvent.clientOrderId(), orderUpdateEvent.exchangeOrderId()).orElse(null);
        if(inFlightOrder == null) {
            handleLostOrder(orderUpdateEvent);
            return;
        }
        OrderState prevState = inFlightOrder.getCurrentState();
        inFlightOrder.updateWithOrderUpdate(orderUpdateEvent);
        if(isOrderCreation(prevState, inFlightOrder)) triggerCreatedEvent(inFlightOrder);
        if(inFlightOrder.isDone()) {
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
        }
    }

    private boolean isOrderCreation(OrderState prevState, InFlightOrder inFlightOrder) {
        return prevState == OrderState.PENDING_CREATE && inFlightOrder.getCurrentState() == OrderState.OPEN;
    }

    public void processOrderNotFound(String clientOrderId) {
        InFlightOrder trackedInFlightOrder = inFlightOrderRepository.findByClientId(clientOrderId).orElse(null);
        if(trackedInFlightOrder == null) return;
        log.warn("주문 (clientId: {} exchangeId: {})을 소실된 주문으로 처리합니다.", clientOrderId, trackedInFlightOrder.getExchangeOrderId());

        if (trackedInFlightOrder != null) {
            orderNotFoundRecords.merge(clientOrderId, 1, Integer::sum);
            if (orderNotFoundRecords.get(clientOrderId) > LOST_ORDER_COUNT_LIMIT && !trackedInFlightOrder.getCurrentState().isTerminal()) {
                processOrderUpdate(new OrderUpdateEvent(
                        trackedInFlightOrder.getTradingPair(),
                        Instant.now(),
                        InFlightOrder.State.FAILED,
                        clientOrderId,
                        null,
                        null
                ));
                lostOrders.put(clientOrderId, trackedInFlightOrder);
            }
            return;
        }
        // active에 없으면 lost에서 확인
        InFlightOrder lostInFlightOrder = lostOrders.get(clientOrderId);
        if (lostInFlightOrder != null) {
            log.info("소실된 주문 {}({})을 제거합니다.", clientOrderId, lostInFlightOrder.getExchangeOrderId());
            lostOrders.remove(clientOrderId);
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


    /**
     * 네트워크 문제등의 이유로 트레킹에 실패한 오더가 뒤늦게 오는 경우 완전히 제거
     */
    private void handleLostOrder(OrderUpdateEvent orderUpdateEvent) {
        Optional<InFlightOrder> optLostOrder = findLostOrder(orderUpdateEvent.clientOrderId(), orderUpdateEvent.exchangeOrderId());
        if (optLostOrder.isEmpty()) return;
        InFlightOrder lostInFlightOrder = optLostOrder.get();
        InFlightOrder.State state = orderUpdateEvent.newState();
        if (state.isTerminal()) {
            lostOrders.remove(lostInFlightOrder.getClientOrderId());
            log.debug("종료된 lost order를 목록에서 제거했습니다: {}", lostInFlightOrder.getClientOrderId());
        }
    }

}