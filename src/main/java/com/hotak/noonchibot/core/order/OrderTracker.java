package com.hotak.noonchibot.core.order;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

@Slf4j
public class OrderTracker implements OrderedLifecycleAware {
    private final EventPublisher eventPublisher;
    private final TradeRepository tradeRepository;
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final Cache<String, OrderView> recentClosedOrders = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .build();
    private final EventSubscriber eventSubscriber;
    private final Map<String, InFlightOrder> inFlightOrders = new HashMap<>();
    private final Set<Subscription> subscriptions = new HashSet<>();


    public OrderTracker(
            EventPublisher eventPublisher,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderSnapshotRepository,
            EventSubscriber eventSubscriber
    ) {
        this.eventPublisher = eventPublisher;
        this.tradeRepository = tradeRepository;
        this.orderSnapshotRepository = orderSnapshotRepository;
        this.eventSubscriber = eventSubscriber;
    }

    void startTrackingOrder(InFlightOrder inFlightOrder) {
        inFlightOrders.put(inFlightOrder.getClientOrderId(), inFlightOrder);
    }

    void processTradeUpdate(TradeEvent.Received event) {
        if (event.clientOrderId() == null && event.exchangeOrderId() == null)
            throw new IllegalArgumentException("TradeUpdate에 오더 아이디 정보가 없습니다.");

        InFlightOrder trackedInFlightOrder = getInFlightOrder(event.clientOrderId(), event.exchangeOrderId());
        if (trackedInFlightOrder == null) {
            log.error("can't find tracked inFlightOrder (client inFlightOrder id: {}, exchange inFlightOrder id: {})", event.clientOrderId(), event.exchangeOrderId());
            return;
        }
        event.fills().forEach(fill -> {
            trackedInFlightOrder.updateWithTradeUpdate(
                    fill.tradeId(),
                    event.clientOrderId(),
                    event.exchangeOrderId(),
                    fill.fillTimestamp(),
                    fill.fillPrice(),
                    fill.fillBaseAmount(),
                    fill.fillQuoteAmount(),
                    fill.fee(),
                    fill.isMaker()
            );
        });
    }

    void processOrderUpdate(OrderEvent.StatusReceived event) {
        InFlightOrder inFlightOrder = findInFlightOrderOrElseThrow(event.clientOrderId(), event.exchangeOrderId());
        OrderState prevState = inFlightOrder.getCurrentState();
        inFlightOrder.updateWithOrderUpdate(
                event.clientOrderId(),
                event.exchangeOrderId(),
                event.orderState(),
                event.timestamp()
        );
        if(isOrderStateNotChanged(prevState, inFlightOrder.getCurrentState())) return;
        eventPublisher.publish(new OrderEvent.SnapshotUpdateRequested(
                inFlightOrder.getClientOrderId(),
                inFlightOrder.getExchangeOrderId(),
                inFlightOrder.getCurrentState(),
                inFlightOrder.getLastUpdateTimestamp()
        ));

        if(inFlightOrder.isDone()) {
            recentClosedOrders.put(inFlightOrder.getClientOrderId(), inFlightOrder.toView());
            inFlightOrders.remove(inFlightOrder.getClientOrderId());
        }
    }

    /**
     * liquidation, adl, settlement 같은 거래소 시스템에서 발생 시킨 이벤트 처리.
     */
    void processSystemOrderOccur() {
        set
    }

    private boolean isOrderStateNotChanged(OrderState prevState, OrderState currentState) {
        return prevState == currentState;
    }

    public InFlightOrder findInFlightOrderOrElseThrow(String clientOrderId, String exchangeOrderId) {
        InFlightOrder inFlightOrder = getInFlightOrder(clientOrderId, exchangeOrderId);
        if(inFlightOrder == null) {
            throw new IllegalArgumentException(
                    String.format("clientOrderId: %s, exchangeOrderId: %s 에 해당하는 주문을 찾을 수 없습니다.",
                            clientOrderId,
                            exchangeOrderId
                    ));
        }
        return inFlightOrder;
    }

    public InFlightOrder getInFlightOrder(String clientOrderId, String exchangeOrderId) {
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
        return getInFlightOrder(clientOrderId, null);
    }

    public InFlightOrder getInFlightOrderByExchangeId(String exchangeOrderId) {
        return getInFlightOrder(null, exchangeOrderId);
    }

    public Collection<InFlightOrder> getAllInFlightOrders() {
        return inFlightOrders.values();
    }

    public Optional<OrderView> getOrderByClientId(String clientOrderId) {
        InFlightOrder inFlightOrder = inFlightOrders.get(clientOrderId);
        if(inFlightOrder != null) return Optional.of(inFlightOrder.toView());
        return Optional.ofNullable(recentClosedOrders.getIfPresent(clientOrderId));
    }

    @Override
    public void onStart() {
        subscriptions.add(
                eventSubscriber.subscribe(OrderEvent.StatusReceived.class, this::processOrderUpdate, ExecutionPolicy.sequential())
        );
        subscriptions.add(
                eventSubscriber.subscribe(TradeEvent.Received.class, this::processTradeUpdate, ExecutionPolicy.sequential())
        );
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public int phase() {
        return Phases.ORDER_TRACKER_SETUP;
    }
}