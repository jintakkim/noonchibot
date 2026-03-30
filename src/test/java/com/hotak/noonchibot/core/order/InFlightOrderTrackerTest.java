package com.hotak.noonchibot.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

class InFlightOrderTrackerTest {

    private List<Object> capturedEvents;
    private OrderTracker tracker;
    private InFlightOrder testInFlightOrder;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();

        ExchangeEventBus exchangeEventBus = Mockito.spy(new ExchangeEventBus());
        doAnswer(invocation -> {
            capturedEvents.add(invocation.getArgument(0));
            return null;
        }).when(exchangeEventBus).publish(any());



        tracker = new OrderTracker(exchangeEventBus, exchangeEventBus);
        testInFlightOrder = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);
    }

    // === 헬퍼 ===

    private OrderUpdateEvent orderUpdate(InFlightOrder.State state, String clientOrderId, String exchangeOrderId) {
        return new OrderUpdateEvent("BTC-USDT", Instant.now(), state, clientOrderId, exchangeOrderId, null);
    }

    private OrderUpdateEvent orderUpdate(InFlightOrder.State state) {
        return orderUpdate(state, "OID-123", "EX-1");
    }

    private OrderUpdateEvent failedOrderUpdate(String clientOrderId, OrderUpdateEvent.OrderFailure failure) {
        return new OrderUpdateEvent("BTC-USDT", Instant.now(), InFlightOrder.State.FAILED, clientOrderId, null, failure);
    }

    private TradeUpdateEvent createFill(String tradeId, String price, String baseAmount, String quoteAmount) {
        return new TradeUpdateEvent(
                tradeId, "OID-123", "EX-1", "BTC-USDT",
                Instant.now(),
                new BigDecimal(price),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                List.of(new TokenAmount("USDT", new BigDecimal("0.05"))),
                true
        );
    }

    private TradeUpdateEvent createFill(String tradeId, String price, String baseAmount, String quoteAmount, List<TokenAmount> fee) {
        return new TradeUpdateEvent(
                tradeId, "OID-123", "EX-1", "BTC-USDT",
                Instant.now(),
                new BigDecimal(price),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                fee,
                true
        );
    }

    @Nested
    @DisplayName("주문 트래킹 관리")
    class TrackingTest {

        @Test
        @DisplayName("주문이 트래킹 목록에 정상적으로 추가된다")
        void startTracking() {
            assertThat(tracker.getActiveOrders()).isEmpty();
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.getActiveOrders()).hasSize(1);
        }

        @Test
        @DisplayName("clientOrderId로 트래킹 중인 주문을 조회한다")
        void findByClientOrderId() {
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.findActiveOrder("OID-123", null))
                    .isPresent()
                    .hasValue(testInFlightOrder);
        }

        @Test
        @DisplayName("exchangeOrderId로 트래킹 중인 주문을 조회한다")
        void findByExchangeOrderId() {
            InFlightOrder inFlightOrderWithExId = new InFlightOrder(
                    "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                    new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), "EX-1");
            tracker.startTrackingOrder(inFlightOrderWithExId);
            assertThat(tracker.findActiveOrder(null, "EX-1"))
                    .isPresent()
                    .hasValue(inFlightOrderWithExId);
        }

        @Test
        @DisplayName("존재하지 않는 주문을 조회하면 empty를 반환한다")
        void findNonExisting() {
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.findActiveOrder("NON-EXIST", null)).isEmpty();
            assertThat(tracker.findActiveOrder(null, "NON-EXIST")).isEmpty();
        }

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId 모두 null이면 empty를 반환한다")
        void findWithBothNull() {
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.findActiveOrder(null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("OrderUpdateEvent 처리 - 주문 생성")
    class InFlightOrderCreationTest {

        @Test
        @DisplayName("OPEN 업데이트 시 BuyOrderCreatedEvent가 발생한다")
        void triggersCreatedEvent() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));

            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
            assertThat(testInFlightOrder.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("exchangeOrderId 없이 clientOrderId로만 주문 생성 이벤트가 발생한다")
        void triggersCreatedEventByExchangeId() {
            InFlightOrder inFlightOrderWithExId = new InFlightOrder(
                    "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                    new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), "EX-1");
            tracker.startTrackingOrder(inFlightOrderWithExId);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, null, "EX-1"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("SELL 주문의 OPEN 업데이트 시 SellOrderCreatedEvent가 발생한다")
        void triggersSellCreatedEvent() {
            InFlightOrder sellInFlightOrder = new InFlightOrder(
                    "OID-SELL", "BTC-USDT", OrderType.LIMIT, TradeType.SELL,
                    new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);
            tracker.startTrackingOrder(sellInFlightOrder);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, "OID-SELL", "EX-1"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(SellOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("PENDING_CREATE 업데이트는 주문 생성 이벤트를 트리거하지 않는다")
        void pendingCreateDoesNotTrigger() {
            tracker.startTrackingOrder(testInFlightOrder);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.PENDING_CREATE, "OID-123", null));

            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(InFlightOrder.State.PENDING_CREATE);
            assertThat(capturedEvents).isEmpty();
        }
    }

    @Nested
    @DisplayName("OrderUpdateEvent 처리 - 주문 종료")
    class InFlightOrderTerminationTest {

        @Test
        @DisplayName("CANCELED 업데이트 시 OrderCanceledEvent가 발생하고 트래킹에서 제거된다")
        void canceledOrder() {
            tracker.startTrackingOrder(testInFlightOrder);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.CANCELED));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(InFlightOrder.State.CANCELED);
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderCanceledEvent.class);
        }

        @Test
        @DisplayName("FAILED 업데이트 시 OrderFailureEvent가 발생하고 트래킹에서 제거된다")
        void failedOrder() {
            tracker.startTrackingOrder(testInFlightOrder);
            OrderUpdateEvent.OrderFailure failure = new OrderUpdateEvent.OrderFailure("ExchangeRejected", "Insufficient balance");

            tracker.processOrderUpdate(failedOrderUpdate("OID-123", failure));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFailureEvent.class);
        }

        @Test
        @DisplayName("FILLED 업데이트 시 BuyOrderCompletedEvent가 발생하고 트래킹에서 제거된다")
        void filledOrder() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));
            capturedEvents.clear();

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.FILLED));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCompletedEvent.class);
        }
    }

    @Nested
    @DisplayName("OrderUpdateEvent 처리 - 예외 케이스")
    class InFlightOrderUpdateEventEdgeCaseTest {

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId 모두 null이면 예외가 발생한다")
        void rejectsBothNull() {
            tracker.startTrackingOrder(testInFlightOrder);

            OrderUpdateEvent invalid = orderUpdate(InFlightOrder.State.FILLED, null, null);

            assertThatThrownBy(() -> tracker.processOrderUpdate(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("트래킹 중이 아닌 주문의 업데이트는 무시된다")
        void unknownOrderIgnored() {
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, "UNKNOWN", "UNKNOWN-EX"));

            assertThat(tracker.getActiveOrders()).isEmpty();
            assertThat(capturedEvents).isEmpty();
        }
    }

    @Nested
    @DisplayName("TradeUpdateEvent 처리")
    class TradeUpdateEventTest {

        @Test
        @DisplayName("체결 시 OrderFilledEvent가 발생하고 수수료 정보를 포함한다")
        void triggersFilledEvent() {
            tracker.startTrackingOrder(testInFlightOrder);

            List<TokenAmount> fee = List.of(new TokenAmount("USDT", new BigDecimal("25.0")));
            TradeUpdateEvent fill = createFill("T-1", "50000.0", "0.5", "25000.0", fee);

            tracker.processTradeUpdate(fill);

            InFlightOrder updated = tracker.findActiveOrder("OID-123", null).orElseThrow();
            assertThat(updated.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFilledEvent.class);
        }

        @Test
        @DisplayName("완전 체결 TradeUpdate만으로는 CompletedEvent가 발생하지 않는다")
        void fullFillDoesNotTriggerCompleted() {
            tracker.startTrackingOrder(testInFlightOrder);

            TradeUpdateEvent fullFill = createFill("T-1", "50000.0", "1.0", "50000.0");
            tracker.processTradeUpdate(fullFill);

            assertThat(testInFlightOrder.isDone()).isTrue();
            assertThat(tracker.getActiveOrders()).containsKey("OID-123");

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFilledEvent.class);

            boolean hasCompleted = capturedEvents.stream()
                    .anyMatch(e -> e instanceof BuyOrderCompletedEvent);
            assertThat(hasCompleted).isFalse();
        }

        @Test
        @DisplayName("트래킹 중이 아닌 주문의 체결 업데이트는 무시된다")
        void unknownOrderTradeIgnored() {
            TradeUpdateEvent fill = createFill("T-1", "50000.0", "1.0", "50000.0");

            tracker.processTradeUpdate(fill);

            assertThat(capturedEvents).isEmpty();
        }
    }

    @Nested
    @DisplayName("체결 + 상태 업데이트 복합 흐름")
    class CombinedFlowTest {

        @Test
        @DisplayName("OPEN → 부분 체결 → 완전 체결 → FILLED 순서로 이벤트가 정확히 발생한다")
        void fullLifecycleFlow() {
            tracker.startTrackingOrder(testInFlightOrder);

            // OPEN
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);

            // 부분 체결
            tracker.processTradeUpdate(createFill("T-1", "50000.0", "0.5", "25000.0"));
            assertThat(capturedEvents).hasSize(2);
            assertThat(capturedEvents.get(1)).isInstanceOf(OrderFilledEvent.class);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.PARTIALLY_FILLED));
            assertThat(tracker.getActiveOrders()).containsKey("OID-123");

            // 완전 체결
            tracker.processTradeUpdate(createFill("T-2", "50000.0", "0.5", "25000.0"));
            assertThat(capturedEvents).hasSize(3);
            assertThat(capturedEvents.get(2)).isInstanceOf(OrderFilledEvent.class);

            // FILLED 상태 확정
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.FILLED));
            assertThat(capturedEvents).hasSize(4);
            assertThat(capturedEvents.get(3)).isInstanceOf(BuyOrderCompletedEvent.class);

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
        }

        @Test
        @DisplayName("TradeUpdate로 수량이 채워져도 OrderUpdateEvent(FILLED)가 와야 CompletedEvent가 발생한다")
        void completedRequiresBothFillAndStateUpdate() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));
            capturedEvents.clear();

            // 수량은 다 채워짐
            tracker.processTradeUpdate(createFill("T-1", "50000.0", "1.0", "50000.0"));

            assertThat(testInFlightOrder.isDone()).isTrue();
            assertThat(tracker.getActiveOrders()).containsKey("OID-123");

            long completedCount = capturedEvents.stream()
                    .filter(e -> e instanceof BuyOrderCompletedEvent).count();
            assertThat(completedCount).isZero();

            // FILLED 상태 도착
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.FILLED));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            completedCount = capturedEvents.stream()
                    .filter(e -> e instanceof BuyOrderCompletedEvent).count();
            assertThat(completedCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("주문 미발견 (InFlightOrder Not Found)")
    class InFlightOrderNotFoundTest {

        @Test
        @DisplayName("존재하지 않는 주문 ID로 호출하면 아무 작업도 하지 않는다")
        void unknownOrderId() {
            tracker.processOrderNotFound("UNKNOWN");

            assertThat(tracker.getActiveOrders()).isEmpty();
            assertThat(tracker.getLostOrders()).isEmpty();
        }

        @Test
        @DisplayName("미발견 횟수가 한계치 미만이면 트래킹을 유지한다")
        void belowLimit() {
            tracker.startTrackingOrder(testInFlightOrder);

            tracker.processOrderNotFound("OID-123");

            assertThat(tracker.getActiveOrders()).containsKey("OID-123");
            assertThat(tracker.getLostOrders()).isEmpty();
        }

        @Test
        @DisplayName("미발견 횟수가 한계치를 초과하면 FAILED 처리 후 lost 목록으로 이동한다")
        void exceedsLimit() {
            tracker.startTrackingOrder(testInFlightOrder);

            tracker.processOrderNotFound("OID-123");
            tracker.processOrderNotFound("OID-123");
            tracker.processOrderNotFound("OID-123");

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(tracker.getLostOrders()).containsKey("OID-123");
            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
        }
    }

    @Nested
    @DisplayName("유실된 주문 (Lost Orders)")
    class LostInFlightOrderTest {

        @BeforeEach
        void makeLostOrder() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderNotFound("OID-123");
            tracker.processOrderNotFound("OID-123");
            tracker.processOrderNotFound("OID-123");
            capturedEvents.clear();
        }

        @Test
        @DisplayName("유실된 주문은 fillable 목록에 포함된다")
        void includedInFillable() {
            assertThat(tracker.getFillableOrders()).containsKey("OID-123");
        }

        @Test
        @DisplayName("FILLED 업데이트가 오면 lost 목록에서 제거된다")
        void removedOnFilled() {
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.FILLED));

            assertThat(tracker.getLostOrders()).doesNotContainKey("OID-123");
        }

        @Test
        @DisplayName("CANCELED 업데이트가 오면 lost 목록에서 제거된다")
        void removedOnCanceled() {
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.CANCELED));

            assertThat(tracker.getLostOrders()).doesNotContainKey("OID-123");
        }

        @Test
        @DisplayName("비종료 상태(OPEN, PARTIALLY_FILLED) 업데이트는 lost 목록에서 제거하지 않는다")
        void notRemovedOnNonTerminal() {
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));
            assertThat(tracker.getLostOrders()).containsKey("OID-123");

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.PARTIALLY_FILLED));
            assertThat(tracker.getLostOrders()).containsKey("OID-123");
        }

        @Test
        @DisplayName("lost 주문에 대한 추가 processOrderNotFound 호출 시 제거된다")
        void removedOnSecondNotFound() {
            tracker.processOrderNotFound("OID-123");

            assertThat(tracker.getLostOrders()).doesNotContainKey("OID-123");
        }
    }
}