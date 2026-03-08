package com.hotak.noonchibot.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotak.noonchibot.core.PubSub;
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

class OrderTrackerTest {

    private List<Object> capturedEvents;
    private OrderTracker tracker;
    private InFlightOrder testOrder;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();

        ExchangeEventBus exchangeEventBus = Mockito.spy(new ExchangeEventBus());
        doAnswer(invocation -> {
            capturedEvents.add(invocation.getArgument(0));
            return null;
        }).when(exchangeEventBus).publish(any());



        tracker = new OrderTracker(exchangeEventBus);
        testOrder = new InFlightOrder(
                "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);
    }

    // === 헬퍼 ===

    private OrderUpdate orderUpdate(InFlightOrder.State state, String clientOrderId, String exchangeOrderId) {
        return new OrderUpdate("BTC-USDT", Instant.now(), state, clientOrderId, exchangeOrderId, null);
    }

    private OrderUpdate orderUpdate(InFlightOrder.State state) {
        return orderUpdate(state, "OID-123", "EX-1");
    }

    private OrderUpdate failedOrderUpdate(String clientOrderId, OrderUpdate.OrderFailure failure) {
        return new OrderUpdate("BTC-USDT", Instant.now(), InFlightOrder.State.FAILED, clientOrderId, null, failure);
    }

    private TradeUpdate createFill(String tradeId, String price, String baseAmount, String quoteAmount) {
        return new TradeUpdate(
                tradeId, "OID-123", "EX-1", "BTC-USDT",
                Instant.now(),
                new BigDecimal(price),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                List.of(new TokenAmount("USDT", new BigDecimal("0.05"))),
                true
        );
    }

    private TradeUpdate createFill(String tradeId, String price, String baseAmount, String quoteAmount, List<TokenAmount> fee) {
        return new TradeUpdate(
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
            tracker.startTrackingOrder(testOrder);
            assertThat(tracker.getActiveOrders()).hasSize(1);
        }

        @Test
        @DisplayName("clientOrderId로 트래킹 중인 주문을 조회한다")
        void findByClientOrderId() {
            tracker.startTrackingOrder(testOrder);
            assertThat(tracker.findActiveOrder("OID-123", null))
                    .isPresent()
                    .hasValue(testOrder);
        }

        @Test
        @DisplayName("exchangeOrderId로 트래킹 중인 주문을 조회한다")
        void findByExchangeOrderId() {
            InFlightOrder orderWithExId = new InFlightOrder(
                    "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                    new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), "EX-1");
            tracker.startTrackingOrder(orderWithExId);
            assertThat(tracker.findActiveOrder(null, "EX-1"))
                    .isPresent()
                    .hasValue(orderWithExId);
        }

        @Test
        @DisplayName("존재하지 않는 주문을 조회하면 empty를 반환한다")
        void findNonExisting() {
            tracker.startTrackingOrder(testOrder);
            assertThat(tracker.findActiveOrder("NON-EXIST", null)).isEmpty();
            assertThat(tracker.findActiveOrder(null, "NON-EXIST")).isEmpty();
        }

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId 모두 null이면 empty를 반환한다")
        void findWithBothNull() {
            tracker.startTrackingOrder(testOrder);
            assertThat(tracker.findActiveOrder(null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("OrderUpdate 처리 - 주문 생성")
    class OrderCreationTest {

        @Test
        @DisplayName("OPEN 업데이트 시 BuyOrderCreatedEvent가 발생한다")
        void triggersCreatedEvent() {
            tracker.startTrackingOrder(testOrder);
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));

            assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.OPEN);
            assertThat(testOrder.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("exchangeOrderId 없이 clientOrderId로만 주문 생성 이벤트가 발생한다")
        void triggersCreatedEventByExchangeId() {
            InFlightOrder orderWithExId = new InFlightOrder(
                    "OID-123", "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                    new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), "EX-1");
            tracker.startTrackingOrder(orderWithExId);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, null, "EX-1"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("SELL 주문의 OPEN 업데이트 시 SellOrderCreatedEvent가 발생한다")
        void triggersSellCreatedEvent() {
            InFlightOrder sellOrder = new InFlightOrder(
                    "OID-SELL", "BTC-USDT", OrderType.LIMIT, TradeType.SELL,
                    new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now(), null);
            tracker.startTrackingOrder(sellOrder);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN, "OID-SELL", "EX-1"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(SellOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("PENDING_CREATE 업데이트는 주문 생성 이벤트를 트리거하지 않는다")
        void pendingCreateDoesNotTrigger() {
            tracker.startTrackingOrder(testOrder);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.PENDING_CREATE, "OID-123", null));

            assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.PENDING_CREATE);
            assertThat(capturedEvents).isEmpty();
        }
    }

    @Nested
    @DisplayName("OrderUpdate 처리 - 주문 종료")
    class OrderTerminationTest {

        @Test
        @DisplayName("CANCELED 업데이트 시 OrderCanceledEvent가 발생하고 트래킹에서 제거된다")
        void canceledOrder() {
            tracker.startTrackingOrder(testOrder);

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.CANCELED));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.CANCELED);
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderCanceledEvent.class);
        }

        @Test
        @DisplayName("FAILED 업데이트 시 OrderFailureEvent가 발생하고 트래킹에서 제거된다")
        void failedOrder() {
            tracker.startTrackingOrder(testOrder);
            OrderUpdate.OrderFailure failure = new OrderUpdate.OrderFailure("ExchangeRejected", "Insufficient balance");

            tracker.processOrderUpdate(failedOrderUpdate("OID-123", failure));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFailureEvent.class);
        }

        @Test
        @DisplayName("FILLED 업데이트 시 BuyOrderCompletedEvent가 발생하고 트래킹에서 제거된다")
        void filledOrder() {
            tracker.startTrackingOrder(testOrder);
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));
            capturedEvents.clear();

            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.FILLED));

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCompletedEvent.class);
        }
    }

    @Nested
    @DisplayName("OrderUpdate 처리 - 예외 케이스")
    class OrderUpdateEdgeCaseTest {

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId 모두 null이면 예외가 발생한다")
        void rejectsBothNull() {
            tracker.startTrackingOrder(testOrder);

            OrderUpdate invalid = orderUpdate(InFlightOrder.State.FILLED, null, null);

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
    @DisplayName("TradeUpdate 처리")
    class TradeUpdateTest {

        @Test
        @DisplayName("체결 시 OrderFilledEvent가 발생하고 수수료 정보를 포함한다")
        void triggersFilledEvent() {
            tracker.startTrackingOrder(testOrder);

            List<TokenAmount> fee = List.of(new TokenAmount("USDT", new BigDecimal("25.0")));
            TradeUpdate fill = createFill("T-1", "50000.0", "0.5", "25000.0", fee);

            tracker.processTradeUpdate(fill);

            InFlightOrder updated = tracker.findActiveOrder("OID-123", null).orElseThrow();
            assertThat(updated.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFilledEvent.class);
        }

        @Test
        @DisplayName("완전 체결 TradeUpdate만으로는 CompletedEvent가 발생하지 않는다")
        void fullFillDoesNotTriggerCompleted() {
            tracker.startTrackingOrder(testOrder);

            TradeUpdate fullFill = createFill("T-1", "50000.0", "1.0", "50000.0");
            tracker.processTradeUpdate(fullFill);

            assertThat(testOrder.isDone()).isTrue();
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
            TradeUpdate fill = createFill("T-1", "50000.0", "1.0", "50000.0");

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
            tracker.startTrackingOrder(testOrder);

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
        @DisplayName("TradeUpdate로 수량이 채워져도 OrderUpdate(FILLED)가 와야 CompletedEvent가 발생한다")
        void completedRequiresBothFillAndStateUpdate() {
            tracker.startTrackingOrder(testOrder);
            tracker.processOrderUpdate(orderUpdate(InFlightOrder.State.OPEN));
            capturedEvents.clear();

            // 수량은 다 채워짐
            tracker.processTradeUpdate(createFill("T-1", "50000.0", "1.0", "50000.0"));

            assertThat(testOrder.isDone()).isTrue();
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
    @DisplayName("주문 미발견 (Order Not Found)")
    class OrderNotFoundTest {

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
            tracker.startTrackingOrder(testOrder);

            tracker.processOrderNotFound("OID-123");

            assertThat(tracker.getActiveOrders()).containsKey("OID-123");
            assertThat(tracker.getLostOrders()).isEmpty();
        }

        @Test
        @DisplayName("미발견 횟수가 한계치를 초과하면 FAILED 처리 후 lost 목록으로 이동한다")
        void exceedsLimit() {
            tracker.startTrackingOrder(testOrder);

            tracker.processOrderNotFound("OID-123");
            tracker.processOrderNotFound("OID-123");
            tracker.processOrderNotFound("OID-123");

            assertThat(tracker.getActiveOrders()).doesNotContainKey("OID-123");
            assertThat(tracker.getLostOrders()).containsKey("OID-123");
            assertThat(testOrder.getCurrentState()).isEqualTo(InFlightOrder.State.FAILED);
        }
    }

    @Nested
    @DisplayName("유실된 주문 (Lost Orders)")
    class LostOrderTest {

        @BeforeEach
        void makeLostOrder() {
            tracker.startTrackingOrder(testOrder);
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