package com.hotak.noonchibot.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hotak.noonchibot.core.PubSub;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.trade.fee.TradeFee;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

class OrderTrackerTest {
    private List<Object> capturedEvents;
    private OrderTracker testTracker;
    private InFlightOrder testOrder;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();

        PubSub spyPubSub = new PubSub() {
            @Override
            public void triggerEvent(Object event) {
                capturedEvents.add(event);
            }
        };
        testTracker = new OrderTracker(spyPubSub);
        testOrder = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);
    }

    @Test
    @DisplayName("주문이 트래킹 목록에 정상적으로 추가된다.")
    void testStartTrackingOrder() {
        assertThat(testTracker.getAllOrders()).isEmpty();

        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);
    }

    @Test
    @DisplayName("주문을 트래킹 목록에서 정상적으로 삭제된다.")
    void testStopTrackingOrder() {
        assertThat(testTracker.getAllOrders()).isEmpty();

        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        testTracker.stopTrackingOrder(testOrder.getClientOrderId());
        assertThat(testTracker.getAllOrders()).isEmpty();
    }

    @Test
    @DisplayName("트래킹 중인 주문을 정상적으로 페치한다.")
    void testFetchTrackingOrder() {
        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder =testTracker.fetchTrackedOrder(testOrder.getClientOrderId());
        assertThat(fetchedOrder).isEqualTo(testOrder);
        assertThat(fetchedOrder.getClientOrderId()).isEqualTo("OID-123");
    }

    @Test
    @DisplayName("트래킹 중이 아닌 주문을 페치할 시 null을 반환한다.")
    void testFetchNonExistingTrackingOrder() {
        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder = testTracker.fetchTrackedOrder("nonExistingId");
        assertThat(fetchedOrder).isNull();
    }

    @Test
    @DisplayName("주문을 client id를 통해 정상적으로 반환한다.")
    void testFetchOrderWithClientId() {
        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder = testTracker.fetchOrder(testOrder.getClientOrderId(), null);
        assertThat(fetchedOrder).isEqualTo(testOrder);
        assertThat(fetchedOrder.getClientOrderId()).isEqualTo("OID-123");
    }

    @Test
    @DisplayName("존재하지 않는 주문을 client id를 통해 페치할 시 null을 반환한다.")
    void testFetchNonExistingOrderWithClientId() {
        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder = testTracker.fetchOrder("nonExistingId", null);
        assertThat(fetchedOrder).isNull();
    }

    @Test
    @DisplayName("주문을 exchange id를 통해 정상적으로 반환한다.")
    void testFetchTrackingOrderWithExchangeId() {
        InFlightOrder orderWithExchangeId = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), "EX-ID-12345");

        testTracker.startTrackingOrder(orderWithExchangeId);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder = testTracker.fetchOrder(null, orderWithExchangeId.getExchangeOrderId());
        assertThat(fetchedOrder).isEqualTo(orderWithExchangeId);
        assertThat(fetchedOrder.getExchangeOrderId()).isEqualTo("EX-ID-12345");
    }

    @Test
    @DisplayName("존재하지 않는 주문을 exchange id를 통해 페치할 시 null을 반환한다.")
    void testFetchNonExistingOrderWithExchangeId() {
        testTracker.startTrackingOrder(testOrder);

        assertThat(testTracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder = testTracker.fetchOrder(null, "nonExistingId");
        assertThat(fetchedOrder).isNull();
    }

    @Test
    @DisplayName("Client ID와 Exchange ID가 모두 누락된 업데이트는 무시된다.")
    void testProcessOrderUpdateInvalidOrderUpdate() {
        testTracker.startTrackingOrder(testOrder);
        InFlightOrder.State originalState = testOrder.getCurrentState();

        OrderUpdate invalidUpdate = new OrderUpdate("BTC-USDT", Instant.now(),
                InFlightOrder.State.FILLED, null, null, Map.of()
        );

        testTracker.processOrderUpdate(invalidUpdate);

        InFlightOrder fetchedOrder = testTracker.fetchTrackedOrder(testOrder.getClientOrderId());
        assertThat(fetchedOrder.getCurrentState()).isEqualTo(originalState);
        assertThat(fetchedOrder.getCurrentState()).isNotEqualTo(InFlightOrder.State.FILLED);

        assertThat(testTracker.getAllOrders()).hasSize(1);
    }

    @Test
    @DisplayName("트래킹 중이 아닌 주문의 업데이트가 들어오면 소실된 주문으로 처리 시도한다.")
    void testProcessOrderUpdateOrderNotFound() {
        OrderUpdate unknownOrderUpdate = new OrderUpdate("BTC-USDT", Instant.now(),
                InFlightOrder.State.OPEN, "Unknown-Client-ID", "Unknown-Exchange-ID", Map.of()
        );

        testTracker.processOrderUpdate(unknownOrderUpdate);
        assertThat(testTracker.getAllOrders()).isEmpty();
        assertThat(testTracker.fetchTrackedOrder("Unknown-Client-ID")).isNull();
    }

    @Test
    @DisplayName("주문 생성 업데이트 시 이벤트를 트리거하고 상태를 업데이트한다.")
    void testProcessOrderUpdateTriggerOrderCreationEvent() {
        testTracker.startTrackingOrder(testOrder);

        String exchangeOrderId = "someExchangeOrderId";

        OrderUpdate orderCreationUpdate = new OrderUpdate(
                testOrder.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.OPEN,
                testOrder.getClientOrderId(),
                exchangeOrderId,
                Map.of()
        );

        testTracker.processOrderUpdate(orderCreationUpdate);

        InFlightOrder updatedOrder = testTracker.fetchTrackedOrder(testOrder.getClientOrderId());

        assertThat(updatedOrder.getClientOrderId()).isNotNull();
        assertThat(updatedOrder.getClientOrderId()).isEqualTo(testOrder.getClientOrderId());
        assertThat(updatedOrder.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
        assertThat(updatedOrder.isDone()).isFalse();

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
    }

    @Test
    @DisplayName("클라이언트 오더 아이디가 없는 주문 생성 업데이트 시 이벤트를 트리거하고 상태를 업데이트한다.")
    void testProcessOrderUpdateTriggerOrderCreationEventWithoutClientOrderId() {
        InFlightOrder orderWithExchangeId = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), "EX-ID-12345");

        testTracker.startTrackingOrder(orderWithExchangeId);

        OrderUpdate orderCreationUpdate = new OrderUpdate(
                orderWithExchangeId.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.OPEN,
                null,
                orderWithExchangeId.getExchangeOrderId(),
                Map.of()
        );

        testTracker.processOrderUpdate(orderCreationUpdate);

        InFlightOrder updatedOrder = testTracker.fetchTrackedOrder(orderWithExchangeId.getClientOrderId());

        assertThat(updatedOrder.getExchangeOrderId()).isNotNull();
        assertThat(updatedOrder.getExchangeOrderId()).isEqualTo(orderWithExchangeId.getExchangeOrderId());
        assertThat(updatedOrder.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
        assertThat(updatedOrder.isDone()).isFalse();

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
    }

    @Test
    @DisplayName("State가 PENDING_CREATE인 주문 업데이트는 주문 생성 이벤트를 트리거하지 않는다.")
    void testProcessOrderUpdateWithPendingCreateDoesNotTriggerOrderCreationEvent() {
        testTracker.startTrackingOrder(testOrder);

        OrderUpdate orderPendingUpdate = new OrderUpdate(
                testOrder.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.PENDING_CREATE,
                testOrder.getClientOrderId(),
                testOrder.getExchangeOrderId(),
                Map.of()
        );

        testTracker.processOrderUpdate(orderPendingUpdate);

        InFlightOrder updatedOrder = testTracker.fetchTrackedOrder(testOrder.getClientOrderId());

        assertThat(updatedOrder.getClientOrderId()).isNotNull();
        assertThat(updatedOrder.getClientOrderId()).isEqualTo(testOrder.getClientOrderId());
        assertThat(updatedOrder.getCurrentState()).isEqualTo(InFlightOrder.State.PENDING_CREATE);
        assertThat(updatedOrder.isDone()).isFalse();

        assertThat(capturedEvents).isEmpty();
    }

    @Test
    @DisplayName("주문 취소 업데이트 시 이벤트를 트리거하고 상태를 업데이트한다.")
    void testProcessOrderUpdateTriggerOrderCanceledEvent() {
        testTracker.startTrackingOrder(testOrder);

        String exchangeOrderId = "someExchangeOrderId";

        OrderUpdate orderCanceledUpdate = new OrderUpdate(
                testOrder.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.CANCELED,
                testOrder.getClientOrderId(),
                exchangeOrderId,
                Map.of()
        );

        testTracker.processOrderUpdate(orderCanceledUpdate);

        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());
        assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.CANCELED);
        assertThat(testOrder.isDone()).isTrue();

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.getFirst()).isInstanceOf(OrderCanceledEvent.class);
    }

    @Test
    @DisplayName("주문 실패 업데이트 시 이벤트를 트리거하고 상태를 업데이트한다.")
    void testProcessOrderUpdateTriggerOrderFailureEvent() {
        testTracker.startTrackingOrder(testOrder);

        String exchangeOrderId = "someExchangeOrderId";

        OrderUpdate orderFailureUpdate = new OrderUpdate(
                testOrder.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.FAILED,
                testOrder.getClientOrderId(),
                exchangeOrderId,
                Map.of()
        );

        testTracker.processOrderUpdate(orderFailureUpdate);

        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());
        assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
        assertThat(testOrder.isDone()).isTrue();

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFailureEvent.class);
    }

    @Test
    @DisplayName("OrderUpdate로 주문 완료 시, Filled 이벤트 없이 Completed 이벤트만 발생한다.")
    void testProcessOrderUpdateTriggerCompletedEventAndNotFillEvent() {
        testTracker.startTrackingOrder(testOrder);

        OrderUpdate partialUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(), InFlightOrder.State.PARTIALLY_FILLED,
                testOrder.getClientOrderId(), testOrder.getExchangeOrderId(), Map.of()
        );
        testTracker.processOrderUpdate(partialUpdate);

        assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.PARTIALLY_FILLED);
        assertThat(testOrder.isDone()).isFalse();

        OrderUpdate filledUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(), InFlightOrder.State.FILLED,
                testOrder.getClientOrderId(), testOrder.getExchangeOrderId(), Map.of()
        );

        testOrder.getCompletelyFilledEvent().complete(null);

        testTracker.processOrderUpdate(filledUpdate);

        assertThat(testTracker.fetchTrackedOrder(testOrder.getClientOrderId())).isNull();

        long filledEventCount = capturedEvents.stream()
                .filter(e -> e instanceof OrderFilledEvent).count();
        long completedEventCount = capturedEvents.stream()
                .filter(e -> e instanceof BuyOrderCompletedEvent).count();

        assertThat(filledEventCount).isEqualTo(0);
        assertThat(completedEventCount).isEqualTo(1);

        BuyOrderCompletedEvent completedEvent = (BuyOrderCompletedEvent) capturedEvents.stream()
                .filter(e -> e instanceof BuyOrderCompletedEvent)
                .findFirst().orElseThrow();

        assertThat(completedEvent.orderId()).isEqualTo(testOrder.getClientOrderId());
    }

    @Test
    @DisplayName("TradeUpdate 처리 시 OrderFilledEvent를 트리거하고 수수료 정보를 포함한다.")
    void testProcessTradeUpdateTriggerFilledEventFlatFee() {
        testTracker.startTrackingOrder(testOrder);
        String exchangeOrderId = "someExchangeOrderId";

        TradeFeeSchema schema = new TradeFeeSchema(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                List.of(),
                List.of()
        );

        BigDecimal fillPrice = testOrder.getPrice();
        BigDecimal fillAmount = testOrder.getAmount().divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        BigDecimal fillQuoteAmount = fillPrice.multiply(fillAmount);

        TradeFee tradeFee = TradeFee.newSpotFee(
                schema,
                testOrder.getTradeType(),
                new BigDecimal("0.001"),
                "USDT",
                List.of(new TokenAmount("USDT", new BigDecimal("0.05")))
        );

        TradeUpdate tradeUpdate = new TradeUpdate(
                "TRADE-1",
                testOrder.getClientOrderId(),
                exchangeOrderId,
                testOrder.getTradingPair(),
                Instant.now(),
                fillPrice,
                fillAmount,
                fillQuoteAmount,
                tradeFee,
                true
        );

        testTracker.processTradeUpdate(tradeUpdate);

        InFlightOrder updatedOrder = testTracker.fetchTrackedOrder(testOrder.getClientOrderId());
        assertThat(updatedOrder.getExecutedAmountBase()).isEqualByComparingTo(fillAmount);

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFilledEvent.class);

        OrderFilledEvent event = (OrderFilledEvent) capturedEvents.getFirst();
        assertThat(event.orderId()).isEqualTo(testOrder.getClientOrderId());
        assertThat(event.tradeFee()).isEqualTo(tradeFee);
    }

    @Test
    @DisplayName("완전 체결 시 Filled 이벤트는 발생하지만 주문은 아직 활성 상태로 남는다.")
    void testProcessTradeUpdateDoesNotTriggerCompletedEventWhenCompletelyFilled() {
        testTracker.startTrackingOrder(testOrder);
        String exchangeOrderId = "someExchangeOrderId";

        BigDecimal fillAmount = testOrder.getAmount();
        BigDecimal fillPrice = testOrder.getPrice();

        TradeFeeSchema schema = new TradeFeeSchema(null, null, null, false, null, null);
        TradeFee tradeFee = TradeFee.newSpotFee(
                schema, testOrder.getTradeType(), new BigDecimal("0.001"), "USDT", List.of()
        );

        TradeUpdate tradeUpdate = new TradeUpdate(
                "TRADE-FULL", testOrder.getClientOrderId(), exchangeOrderId,
                testOrder.getTradingPair(), Instant.now(),
                fillPrice, fillAmount, fillPrice.multiply(fillAmount), tradeFee, true
        );

        testTracker.processTradeUpdate(tradeUpdate);

        assertThat(testOrder.isDone()).isTrue();

        assertThat(testTracker.getAllOrders()).containsKey(testOrder.getClientOrderId());

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFilledEvent.class);

        boolean hasCompletedEvent = capturedEvents.stream()
                .anyMatch(e -> e instanceof BuyOrderCompletedEvent);
        assertThat(hasCompletedEvent).isFalse();
    }

    @Test
    @DisplayName("OrderUpdate와 TradeUpdate가 순차적으로 발생해도 트래킹 상태를 정확히 유지한다.")
    void testUpdatingOrderStatesWithBothUpdates() {
        testTracker.startTrackingOrder(testOrder);

        String exId = "someExchangeOrderId";
        OrderUpdate orderCreationUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(), InFlightOrder.State.OPEN,
                testOrder.getClientOrderId(), exId, Map.of()
        );
        testTracker.processOrderUpdate(orderCreationUpdate);

        InFlightOrder openOrder = testTracker.fetchTrackedOrder(testOrder.getClientOrderId());
        assertThat(openOrder.getExchangeOrderId()).isEqualTo(exId);
        assertThat(openOrder.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
        assertThat(openOrder.getCurrentState()).isNotEqualTo(InFlightOrder.State.PENDING_CREATE);
        assertThat(openOrder.getExecutedAmountBase()).isEqualByComparingTo(BigDecimal.ZERO);

        BigDecimal fillPrice = testOrder.getPrice();
        BigDecimal fillAmount = testOrder.getAmount();
        TradeFee tradeFee = TradeFee.newSpotFee(
                new TradeFeeSchema(null, null, null, false, null, null),
                testOrder.getTradeType(), new BigDecimal("0.001"), "USDT", List.of()
        );

        TradeUpdate tradeUpdate = new TradeUpdate(
                "TID-1", testOrder.getClientOrderId(), exId, testOrder.getTradingPair(),
                Instant.now(), fillPrice, fillAmount, fillPrice.multiply(fillAmount), tradeFee, true
        );
        testTracker.processTradeUpdate(tradeUpdate);

        assertThat(testTracker.getAllOrders()).containsKey(testOrder.getClientOrderId());

        assertThat(testOrder.isDone()).isTrue();
        assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
    }

    @Test
    @DisplayName("관리 목록에 없는 주문 ID로 processOrderNotFound 호출 시 아무 작업도 하지 않는다.")
    void testProcessOrderNotFoundInvalidOrder() {
        assertThat(testTracker.getAllOrders()).isEmpty();

        String unknownOrderId = "UNKNOWN_ORDER_ID";

        testTracker.processOrderNotFound(unknownOrderId);

        assertThat(testTracker.getAllOrders()).isEmpty();
    }

    @Test
    @DisplayName("주문 미발견 횟수가 한계치 미만이면 트래킹을 유지한다.")
    void testProcessOrderNotFoundDoesNotExceedLimit() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getAllOrders()).containsKey(testOrder.getClientOrderId());
    }

    @Test
    @DisplayName("주문 미발견 횟수가 한계치를 초과하면 트래킹을 중단한다.")
    void testProcessOrderNotFoundExceededLimit() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());

        assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
    }

    @Test
    @DisplayName("주문 미발견 횟수가 한계치를 초과하면 LostOrders 목록에 추가된다.")
    void testAccessLostOrders() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getLostOrders()).hasSize(1);
        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
    }

    @Test
    @DisplayName("유실된 주문은 체결 가능 목록(active + lost)에 포함되어야 한다.")
    void testLostOrdersReturnedInAllFillableOrders() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        Map<String, InFlightOrder> allFillableOrders = testTracker.getActiveAndLostOrders();
        assertThat(allFillableOrders).containsKey(testOrder.getClientOrderId());
    }

    @Test
    @DisplayName("유실된 주문이라도 완전 체결(FILLED) 업데이트가 오면 목록에서 제거된다.")
    void testLostOrderRemovedWhenFullyFilled() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        OrderUpdate completionUpdate = new OrderUpdate(
                testOrder.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.FILLED,
                testOrder.getClientOrderId(),
                testOrder.getExchangeOrderId(),
                Map.of()
        );

        testTracker.processOrderUpdate(completionUpdate);

        assertThat(testTracker.getLostOrders()).doesNotContainKey(testOrder.getClientOrderId());
        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());
    }

    @Test
    @DisplayName("유실된 주문이라도 취소(CANCELED) 업데이트가 오면 목록에서 제거된다.")
    void testLostOrderRemovedWhenCanceled() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        OrderUpdate cancelUpdate = new OrderUpdate(
                testOrder.getTradingPair(),
                Instant.now(),
                InFlightOrder.State.CANCELED,
                testOrder.getClientOrderId(),
                testOrder.getExchangeOrderId(),
                Map.of()
        );

        testTracker.processOrderUpdate(cancelUpdate);

        assertThat(testTracker.getLostOrders()).doesNotContainKey(testOrder.getClientOrderId());
        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());
    }

    @Test
    @DisplayName("유실된 주문에 진행 중인 상태(OPEN, PARTIAL 등) 업데이트가 와도 목록에서 제거되지 않는다.")
    void testLostOrderNotRemovedWhenUpdatedWithNonFinalStates() {
        testTracker.startTrackingOrder(testOrder);

        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());
        testTracker.processOrderNotFound(testOrder.getClientOrderId());

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        // Open case
        OrderUpdate openUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(),
                InFlightOrder.State.OPEN,
                testOrder.getClientOrderId(), testOrder.getExchangeOrderId(), Map.of()
        );
        testTracker.processOrderUpdate(openUpdate);

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        // Partially Filled case
        OrderUpdate partialUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(),
                InFlightOrder.State.PARTIALLY_FILLED,
                testOrder.getClientOrderId(), testOrder.getExchangeOrderId(), Map.of()
        );
        testTracker.processOrderUpdate(partialUpdate);

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());

        // Cancel case
        OrderUpdate pendingCancelUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(),
                InFlightOrder.State.PENDING_CANCEL,
                testOrder.getClientOrderId(), testOrder.getExchangeOrderId(), Map.of()
        );
        testTracker.processOrderUpdate(pendingCancelUpdate);

        assertThat(testTracker.getLostOrders()).containsKey(testOrder.getClientOrderId());
    }

    @Test
    @DisplayName("주문 완료(FILLED) 상태가 체결 데이터보다 먼저 와도, 체결이 완료될 때까지 처리를 지연시킨다.")
    void testUpdateToCloseOrderIsNotProcessedUntilOrderCompletelyFilled() throws ExecutionException, InterruptedException {
        testTracker.startTrackingOrder(testOrder);
        String exId = "someExchangeOrderId";

        // PENDING_CREATE 에서 OPEN 상태로 바뀌는 이벤트 미리 처리
        // BuyOrderCreatedEvent를 생략한다
        testTracker.processOrderUpdate(new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(),
                InFlightOrder.State.OPEN,
                testOrder.getClientOrderId(), exId, Map.of()
        ));
        capturedEvents.clear();

        OrderUpdate misorderedCompletionUpdate = new OrderUpdate(
                testOrder.getTradingPair(), Instant.now(),
                InFlightOrder.State.FILLED,
                testOrder.getClientOrderId(), exId, Map.of()
        );

        CompletableFuture<Void> updateFuture = CompletableFuture.runAsync(() -> {
            testTracker.processOrderUpdate(misorderedCompletionUpdate);
        });

        Thread.sleep(100);

        InFlightOrder trackedOrder = testTracker.fetchTrackedOrder(testOrder.getClientOrderId());
        assertThat(trackedOrder).isNotNull();
        assertThat(trackedOrder.isDone()).isFalse();
        assertThat(updateFuture.isDone()).isFalse();

        TradeFee fee = TradeFee.newSpotFee(
                new TradeFeeSchema(null, null, null, false, null, null),
                TradeType.BUY, BigDecimal.ZERO, "USDT", List.of()
        );
        TradeUpdate tradeUpdate = new TradeUpdate(
                "TID-1", testOrder.getClientOrderId(), exId, testOrder.getTradingPair(),
                Instant.now(), testOrder.getPrice(), testOrder.getAmount(), // 100% 수량
                BigDecimal.TEN, fee, true
        );

        testTracker.processTradeUpdate(tradeUpdate);

        assertDoesNotThrow(() -> updateFuture.get(2, TimeUnit.SECONDS));

        assertThat(testTracker.getAllOrders()).doesNotContainKey(testOrder.getClientOrderId());

        assertThat(capturedEvents).hasSize(2);
        assertThat(capturedEvents.get(0)).isInstanceOf(OrderFilledEvent.class);
        assertThat(capturedEvents.get(1)).isInstanceOf(BuyOrderCompletedEvent.class);
    }


}