package com.hotak.noonchibot.core.order;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.api.OrderQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class OrderTracker implements LifecycleAware, OrderQueryApi {
    private final EventPublisher eventPublisher;
    private final TradeRepository tradeRepository;
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final Cache<String, InFlightOrder> settlingOrders = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(1000)
            .build();
    private final EventSubscriber eventSubscriber;
    private final Map<String, InFlightOrder> inFlightOrders = new ConcurrentHashMap<>();
    private final Set<Subscription> subscriptions = new HashSet<>();

    void startTrackingOrder(InFlightOrder inFlightOrder) {
        inFlightOrders.put(inFlightOrder.getClientOrderId(), inFlightOrder);
    }

    void processTradeUpdate(TradeEvent.Received event) {
        if (event.clientOrderId() == null && event.exchangeOrderId() == null)
            throw new IllegalArgumentException("TradeUpdate에 오더 아이디 정보가 없습니다.");

        InFlightOrder trackedInFlightOrder = getOrderForUpdate(event.clientOrderId(), event.exchangeOrderId());
        if (trackedInFlightOrder == null) {
            log.warn(
                    "트랙킹 하지 않는 주문은 무시합니다. clientOrderId={}, exchangeOrderId={}",
                    event.clientOrderId(),
                    event.exchangeOrderId()
            );
            return;
        }
        boolean updated = false;
        for (TradeEvent.Fill fill : event.fills()) {
            updated |= trackedInFlightOrder.updateWithTradeUpdate(
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
        }
        if (updated) {
            publishSnapshotUpdate(trackedInFlightOrder);
        }
    }

    void processOrderUpdate(OrderEvent.StatusReceived event) {
        InFlightOrder inFlightOrder = getOrderForUpdate(event.clientOrderId(), event.exchangeOrderId());
        if (inFlightOrder == null) {
            log.debug(
                    "ignoring order update for untracked order. clientOrderId={}, exchangeOrderId={}",
                    event.clientOrderId(),
                    event.exchangeOrderId()
            );
            return;
        }
        boolean updated = inFlightOrder.updateWithOrderUpdate(
                event.clientOrderId(),
                event.exchangeOrderId(),
                event.orderState(),
                event.timestamp()
        );
        if (!updated) return;
        publishSnapshotUpdate(inFlightOrder);

        if (inFlightOrder.isDone() && inFlightOrders.get(inFlightOrder.getClientOrderId()) == inFlightOrder) {
            settlingOrders.put(inFlightOrder.getClientOrderId(), inFlightOrder);
            inFlightOrders.remove(inFlightOrder.getClientOrderId(), inFlightOrder);
        }
    }

    private void publishSnapshotUpdate(InFlightOrder order) {
        eventPublisher.publish(new OrderEvent.SnapshotUpdateRequested(order.toView()));
    }

    public void restore(InFlightOrder order) {
        inFlightOrders.put(order.getClientOrderId(), order);
    }

    /**
     * liquidation, adl, settlement 같은 거래소 시스템에서 발생 시킨 이벤트 처리.
     */
    void processSystemOrderOccur() {

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

    private InFlightOrder getOrderForUpdate(String clientOrderId, String exchangeOrderId) {
        InFlightOrder inFlightOrder = getInFlightOrder(clientOrderId, exchangeOrderId);
        if (inFlightOrder != null) {
            return inFlightOrder;
        }
        if (clientOrderId != null) {
            InFlightOrder settlingOrder = settlingOrders.getIfPresent(clientOrderId);
            if (settlingOrder != null) {
                return settlingOrder;
            }
        }
        if (exchangeOrderId != null) {
            return settlingOrders.asMap().values().stream()
                    .filter(order -> exchangeOrderId.equals(order.getExchangeOrderId()))
                    .findAny()
                    .orElse(null);
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
        return List.copyOf(inFlightOrders.values());
    }

    @Override
    public OrderView getOrderByClientOrderId(String clientOrderId) {
        InFlightOrder inFlightOrder = inFlightOrders.get(clientOrderId);
        if(inFlightOrder != null) return inFlightOrder.toView();
        InFlightOrder settlingOrder = settlingOrders.getIfPresent(clientOrderId);
        if (settlingOrder != null) return settlingOrder.toView();
        return null;
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
