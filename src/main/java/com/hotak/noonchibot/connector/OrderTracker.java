package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.PubSub;
import com.hotak.noonchibot.core.datatype.*; // 관련 클래스 패키지 가정
import com.hotak.noonchibot.core.event.*;    // 이벤트 클래스 패키지 가정
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Getter
@Setter
public class OrderTracker {
    private static final int TRADE_FILLS_WAIT_TIMEOUT = 5;

    private final PubSub eventPublisher;
    private int lostOrderCountLimit = 2;

    private final Map<String, InFlightOrder> inFlightOrders = new ConcurrentHashMap<>();
    private final Map<String, InFlightOrder> lostOrders = new ConcurrentHashMap<>();
    private final Map<String, Integer> orderNotFoundRecords = new ConcurrentHashMap<>();

    public OrderTracker(PubSub eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public Map<String, InFlightOrder> getAllOrders() { return new HashMap<>(inFlightOrders); }

    public void startTrackingOrder(InFlightOrder order) {
        inFlightOrders.put(order.getClientOrderId(), order);
    }

    public void stopTrackingOrder(String clientOrderId) {
        InFlightOrder order = inFlightOrders.remove(clientOrderId);
        if (order != null) {
            orderNotFoundRecords.remove(clientOrderId);
        }
    }

    public InFlightOrder fetchTrackedOrder(String clientOrderId) {
        return inFlightOrders.get(clientOrderId);
    }

    public InFlightOrder fetchOrder(String clientOrderId, String exchangeOrderId) {
        if (clientOrderId != null) {
            return getAllOrders().get(clientOrderId);
        }
        if (exchangeOrderId != null) {
            return getAllOrders().values().stream()
                    .filter(o -> exchangeOrderId.equals(o.getExchangeOrderId()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public InFlightOrder fetchLostOrder(String clientOrderId, String exchangeOrderId) {
        if (clientOrderId != null) {
            return lostOrders.get(clientOrderId);
        }
        if (exchangeOrderId != null) {
            return lostOrders.values().stream()
                    .filter(o -> exchangeOrderId.equals(o.getExchangeOrderId()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public void processTradeUpdate(TradeUpdate tradeUpdate) {
        InFlightOrder trackedOrder = inFlightOrders.get(tradeUpdate.clientOrderId());
        if (trackedOrder != null) {
            BigDecimal prevExecutedAmount = trackedOrder.getExecutedAmountBase();
            trackedOrder.updateWithTradeUpdate(tradeUpdate);
            triggerOrderFills(trackedOrder, prevExecutedAmount, tradeUpdate);
        }
    }

    public void processOrderUpdate(OrderUpdate orderUpdate) {
        if (orderUpdate.clientOrderId() == null && orderUpdate.exchangeOrderId() == null) {
            log.error("OrderUpdate에 오더 아이디 정보가 없습니다.");
        }

        InFlightOrder trackedOrder = fetchOrder(orderUpdate.clientOrderId(), orderUpdate.exchangeOrderId());

        if (trackedOrder != null) {
            if (orderUpdate.newState() == InFlightOrder.State.FILLED && !trackedOrder.isDone()) {
                try {
                    trackedOrder.getCompletelyFilledEvent().get(TRADE_FILLS_WAIT_TIMEOUT, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    log.warn("시간 초과! 체결 정보가 제때 안 왔네요.");
                } catch (InterruptedException e) {
                    log.error("누가 기다리는 나를 깨웠어요! (시스템 종료 등)");
                    Thread.currentThread().interrupt(); // 매너 있게 인터럽트 상태 복구
                } catch (ExecutionException e) {
                    log.error("기다리던 로직 내부에서 에러가 터졌어요: {}", e.getCause().getMessage());
                }
            }

            InFlightOrder.State previousState = trackedOrder.getCurrentState();

            trackedOrder.updateWithOrderUpdate(orderUpdate);
            triggerOrderCreation(trackedOrder, previousState, orderUpdate.newState());
            triggerOrderCompletion(trackedOrder, orderUpdate);
        } else {
            handleLostOrder(orderUpdate);
        }
    }

    public void processOrderNotFound(String clientOrderId) {
        InFlightOrder trackedOrder = fetchTrackedOrder(clientOrderId);

        if (trackedOrder != null) {
            orderNotFoundRecords.merge(clientOrderId, 1, Integer::sum);
            if (orderNotFoundRecords.get(clientOrderId) > lostOrderCountLimit) {
                if (trackedOrder.getCurrentState() != InFlightOrder.State.CANCELED
                        && trackedOrder.getCurrentState() != InFlightOrder.State.FILLED
                        && trackedOrder.getCurrentState() != InFlightOrder.State.FAILED) {

                    log.warn("주문 {}({})을 소실된 주문으로 처리합니다.",
                            clientOrderId, trackedOrder.getExchangeOrderId());

                    OrderUpdate orderUpdate = new OrderUpdate(
                            trackedOrder.getTradingPair(),
                            Instant.now(),
                            InFlightOrder.State.FAILED,
                            clientOrderId,
                            null,
                            null
                    );
                    processOrderUpdate(orderUpdate);
                    lostOrders.put(trackedOrder.getClientOrderId(), trackedOrder);
            }
        } else {
                InFlightOrder lostOrder = lostOrders.get(clientOrderId);
                if (lostOrder != null) {
                    log.info("소실된 주문 {}({})을 찾을 수 없어 제거합니다.",
                            clientOrderId, lostOrder.getExchangeOrderId());
                    OrderUpdate orderUpdate = new OrderUpdate(
                            lostOrder.getTradingPair(),
                            Instant.now(),
                            InFlightOrder.State.FAILED,
                            clientOrderId,
                            null,
                            null
                    );
                    processOrderUpdate(orderUpdate);
                }
            }
        }
    }

    private void triggerCreatedEvent(InFlightOrder order) {
        if (order.getTradeType() == TradeType.BUY) {
            BuyOrderCreatedEvent event = new BuyOrderCreatedEvent(
                    Instant.now(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.triggerEvent(event);

        } else {
            SellOrderCreatedEvent event = new SellOrderCreatedEvent(
                    Instant.now(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.triggerEvent(event);
        }
    }

    private void triggerCancelledEvent(InFlightOrder order) {
        eventPublisher.triggerEvent(new OrderCancelledEvent(Instant.now(), order.getClientOrderId(), order.getExchangeOrderId()));
    }

    private void triggerFilledEvent(InFlightOrder order, TradeUpdate tradeUpdate) {
        eventPublisher.triggerEvent(
                new OrderFilledEvent(
                        Instant.now(), order.getClientOrderId(), order.getTradingPair(),
                        order.getTradeType(), order.getOrderType(), tradeUpdate.fillBaseAmount(), tradeUpdate.fillPrice(),
                         tradeUpdate.tradeFee(), tradeUpdate.tradeId(), tradeUpdate.exchangeOrderId()
                )
        );
    }

    private void triggerCompletedEvent(InFlightOrder order) {
        if (order.getTradeType() == TradeType.BUY) {
            BuyOrderCompletedEvent event = new BuyOrderCompletedEvent(
                    Instant.now(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.triggerEvent(event);

        } else {
            SellOrderCompletedEvent event = new SellOrderCompletedEvent(
                    Instant.now(), order.getOrderType(), order.getTradingPair(),
                    order.getAmount(), order.getPrice(), order.getClientOrderId(),
                    order.getCreationTimestamp(), order.getExchangeOrderId()
            );
            eventPublisher.triggerEvent(event);
        }
    }

    private void triggerFailureEvent(InFlightOrder order, OrderUpdate orderUpdate) {
        Map<String, Object> miscUpdates = orderUpdate.miscUpdates();

        OrderFailureEvent event = new OrderFailureEvent(
                Instant.now(),
                order.getClientOrderId(),
                order.getOrderType(),
                (String) miscUpdates.get("error_message"),
                (String) miscUpdates.get("error_type")
        );

        eventPublisher.triggerEvent(event);
    }

    private void triggerOrderCreation(InFlightOrder trackedOrder, InFlightOrder.State previousState, InFlightOrder.State newState) {
        if (previousState == InFlightOrder.State.PENDING_CREATE &&
                previousState != newState &&
                newState != InFlightOrder.State.CANCELED &&
                newState != InFlightOrder.State.FAILED &&
                newState != InFlightOrder.State.PENDING_CANCEL) {

            triggerCreatedEvent(trackedOrder);
        }
    }

    private void triggerOrderFills(InFlightOrder trackedOrder, BigDecimal prevExecutedAmountBase, TradeUpdate tradeUpdate) {
        if (prevExecutedAmountBase.compareTo(trackedOrder.getExecutedAmountBase()) < 0) {
            triggerFilledEvent(trackedOrder, tradeUpdate);
        }
    }

    private void triggerOrderCompletion(InFlightOrder trackedOrder, OrderUpdate orderUpdate) {
        if (!trackedOrder.isDone()) {
            return;
        }

        InFlightOrder.State state = orderUpdate.newState();

        if (state == InFlightOrder.State.CANCELED || state == InFlightOrder.State.PENDING_CANCEL) {
            triggerCancelledEvent(trackedOrder);
            log.info("Successfully canceled order {}.", trackedOrder.getClientOrderId());

        } else if (state == InFlightOrder.State.FILLED) {
            triggerCompletedEvent(trackedOrder);
            log.info("{} order {} completely filled.",
                    trackedOrder.getTradeType().name().toUpperCase(),
                    trackedOrder.getClientOrderId());

        } else if (state == InFlightOrder.State.FAILED) {
            triggerFailureEvent(trackedOrder, orderUpdate);
            log.info("Order {} has failed. Order Update: {}",
                    trackedOrder.getClientOrderId(), orderUpdate);
        }

        stopTrackingOrder(trackedOrder.getClientOrderId());
    }

    private void handleLostOrder(OrderUpdate orderUpdate) {
        InFlightOrder lostOrder = fetchLostOrder(orderUpdate.clientOrderId(), orderUpdate.exchangeOrderId());
        if (lostOrder != null) {
            InFlightOrder.State state = orderUpdate.newState();
            if (state == InFlightOrder.State.CANCELED || state == InFlightOrder.State.FILLED || state == InFlightOrder.State.FAILED) {
                lostOrders.remove(lostOrder.getClientOrderId());
                log.debug("종료된 로스트 오더를 목록에서 제거했습니다. ({})", lostOrder.getClientOrderId());
            }
        }
    }
}