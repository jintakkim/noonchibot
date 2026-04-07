package com.hotak.noonchibot.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotak.noonchibot.core.TestMainExecutor;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class OrderTrackerTest {

    private List<Object> capturedEvents;
    private OrderTracker tracker;
    private InFlightOrder testInFlightOrder;
    private TradeRepository tradeRepository;
    private OrderHistoryRepository orderHistoryRepository;
    private TestMainExecutor testMainExecutor;

    @BeforeEach
    void setUp() {
        capturedEvents = new ArrayList<>();
        testMainExecutor = new TestMainExecutor();
        ExchangeEventBus exchangeEventBus = Mockito.spy(new ExchangeEventBus());
        doAnswer(invocation -> {
            capturedEvents.add(invocation.getArgument(0));
            return null;
        }).when(exchangeEventBus).publish(any());
        tradeRepository = Mockito.mock(TradeRepository.class);
        orderHistoryRepository = Mockito.mock(OrderHistoryRepository.class);
        tracker = new OrderTracker(
                exchangeEventBus,
                "test-platform",
                tradeRepository,
                orderHistoryRepository,
                testMainExecutor,
                Runnable::run,
                exchangeEventBus
        );
        testInFlightOrder = new InFlightOrder(
                "OID-123",
                "BTC-USDT",
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("1.0"),
                new BigDecimal("50000.0"),
                Instant.now(),
                false,
                TimeInForce.GTC
        );
    }

    // === 헬퍼 ===

    private OrderUpdateEvent orderUpdate(OrderState state, String clientOrderId, String exchangeOrderId) {
        return new OrderUpdateEvent("BTC-USDT", Instant.now(), state, clientOrderId, exchangeOrderId, null);
    }

    private OrderUpdateEvent orderUpdate(OrderState state) {
        return orderUpdate(state, "OID-123", "EX-1");
    }

    private OrderUpdateEvent failedOrderUpdate(String clientOrderId, OrderUpdateEvent.OrderFailure failure) {
        return new OrderUpdateEvent("BTC-USDT", Instant.now(), OrderState.FAILED, clientOrderId, null, failure);
    }

    private TradeUpdateEvent createFill(String tradeId, String price, String baseAmount, String quoteAmount) {
        return new TradeUpdateEvent(
                tradeId, "OID-123", "EX-1", "BTC-USDT",
                Instant.now(),
                new BigDecimal(price),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                new TokenAmount("USDT", new BigDecimal("0.05")),
                true
        );
    }

    private TradeUpdateEvent createFill(String tradeId, String price, String baseAmount, String quoteAmount, TokenAmount fee) {
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
            assertThat(tracker.getAll()).isEmpty();
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.getAll()).hasSize(1);
        }

        @Test
        @DisplayName("clientOrderId로 트래킹 중인 주문을 조회한다")
        void findByClientOrderId() {
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.getInFlightOrderById("OID-123", null))
                    .isEqualTo(testInFlightOrder);
        }

        @Test
        @DisplayName("exchangeOrderId로 트래킹 중인 주문을 조회한다")
        void findByExchangeOrderId() {
            InFlightOrder inFlightOrderWithExId = new InFlightOrder(
                    "OID-123",
                    "BTC-USDT",
                    OrderType.LIMIT,
                    TradeType.BUY,
                    new BigDecimal("1.0"),
                    new BigDecimal("50000.0"),
                    Instant.now(),
                    "EX-1",
                    false,
                    TimeInForce.GTC,
                    new HashSet<>(),
                    new HashMap<>()
            );
            tracker.startTrackingOrder(inFlightOrderWithExId);
            assertThat(tracker.getInFlightOrderById(null, "EX-1"))
                    .isEqualTo(inFlightOrderWithExId);
        }

        @Test
        @DisplayName("존재하지 않는 주문을 조회하면 null를 반환한다")
        void findNonExisting() {
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.getInFlightOrderById("NON-EXIST", null)).isNull();
            assertThat(tracker.getInFlightOrderById(null, "NON-EXIST")).isNull();
        }

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId 모두 null이면 null을 반환한다")
        void findWithBothNull() {
            tracker.startTrackingOrder(testInFlightOrder);
            assertThat(tracker.getInFlightOrderById(null, null)).isNull();
        }
    }

    @Nested
    @DisplayName("OrderUpdateEvent 처리 - 주문 생성")
    class InFlightOrderCreationTest {

        @Test
        @DisplayName("OPEN 업데이트 시 BuyOrderCreatedEvent가 발생한다")
        void triggersCreatedEvent() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN));

            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(OrderState.OPEN);
            assertThat(testInFlightOrder.getExchangeOrderId()).isEqualTo("EX-1");
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("exchangeOrderId 없이 clientOrderId로만 주문 생성 이벤트가 발생한다")
        void triggersCreatedEventByExchangeId() {
            InFlightOrder inFlightOrderWithExId = new InFlightOrder(
                    "OID-123",
                    "BTC-USDT",
                    OrderType.LIMIT,
                    TradeType.BUY,
                    new BigDecimal("1.0"),
                    new BigDecimal("50000.0"),
                    Instant.now(),
                    "EX-1",
                    false,
                    TimeInForce.GTC,
                    new HashSet<>(),
                    new HashMap<>()
            );
            tracker.startTrackingOrder(inFlightOrderWithExId);
            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN, null, "EX-1"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("SELL 주문의 OPEN 업데이트 시 SellOrderCreatedEvent가 발생한다")
        void triggersSellCreatedEvent() {
            InFlightOrder sellInFlightOrder = new InFlightOrder(
                    "OID-SELL",
                    "BTC-USDT",
                    OrderType.LIMIT,
                    TradeType.SELL,
                    new BigDecimal("1.0"),
                    new BigDecimal("50000.0"),
                    Instant.now(),
                    false,
                    TimeInForce.GTC
            );
            tracker.startTrackingOrder(sellInFlightOrder);

            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN, "OID-SELL", "EX-1"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(SellOrderCreatedEvent.class);
        }

        @Test
        @DisplayName("PENDING_CREATE 업데이트는 주문 생성 이벤트를 트리거하지 않는다")
        void pendingCreateDoesNotTrigger() {
            tracker.startTrackingOrder(testInFlightOrder);

            tracker.processOrderUpdate(orderUpdate(OrderState.PENDING_CREATE, "OID-123", null));

            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(OrderState.PENDING_CREATE);
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

            tracker.processOrderUpdate(orderUpdate(OrderState.CANCELED));

            assertThat(tracker.getAll())
                    .extracting(InFlightOrder::getClientOrderId)
                    .doesNotContain("OID-123");
            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(OrderState.CANCELED);
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderCanceledEvent.class);
        }

        @Test
        @DisplayName("FAILED 업데이트 시 OrderFailureEvent가 발생하고 트래킹에서 제거된다")
        void failedOrder() {
            tracker.startTrackingOrder(testInFlightOrder);
            OrderUpdateEvent.OrderFailure failure = new OrderUpdateEvent.OrderFailure("ExchangeRejected", "Insufficient balance");

            tracker.processOrderUpdate(failedOrderUpdate("OID-123", failure));

            assertThat(tracker.getAll())
                    .extracting(InFlightOrder::getClientOrderId)
                    .doesNotContain("OID-123");
            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(OrderState.FAILED);
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFailureEvent.class);
        }

        @Test
        @DisplayName("FILLED 업데이트 시 BuyOrderCompletedEvent가 발생하고 트래킹에서 제거된다")
        void filledOrder() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN));
            capturedEvents.clear();

            tracker.processOrderUpdate(orderUpdate(OrderState.FILLED));

            assertThat(tracker.getAll())
                    .extracting(InFlightOrder::getClientOrderId)
                    .doesNotContain("OID-123");
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCompletedEvent.class);
        }

        @Test
        @DisplayName("done 상태의 주문은 history로 저장된다")
        void doneOrderIsSavedToHistory() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(new OrderUpdateEvent(
                    "BTC-USDT",
                    Instant.now(),
                    OrderState.FILLED,
                    "OID-123",
                    "exchange-1",
                    null
            ));

            verify(orderHistoryRepository).save(argThat(history ->
                    history.getClientOrderId().equals("OID-123")
            ));
        }
    }

    @Nested
    @DisplayName("OrderUpdateEvent 처리 - 예외 케이스")
    class InFlightOrderUpdateEventEdgeCaseTest {

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId 모두 null이면 예외가 발생한다")
        void rejectsBothNull() {
            tracker.startTrackingOrder(testInFlightOrder);
            OrderUpdateEvent invalid = orderUpdate(OrderState.FILLED, null, null);
            assertThatThrownBy(() -> tracker.processOrderUpdate(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("트래킹 중이 아닌 주문의 업데이트는 무시된다")
        void unknownOrderIgnored() {
            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN, "UNKNOWN", "UNKNOWN-EX"));

            assertThat(tracker.getAll()).isEmpty();
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

            TokenAmount fee = new TokenAmount("USDT", new BigDecimal("25.0"));
            TradeUpdateEvent fill = createFill("T-1", "50000.0", "0.5", "25000.0", fee);

            assertThat(testInFlightOrder.getAccumulatedFees()).isEmpty();

            tracker.processTradeUpdate(fill);

            InFlightOrder updated = tracker.getInFlightOrderByClientId("OID-123");
            assertThat(updated.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));
            assertThat(updated.getAccumulatedFees()).containsEntry("USDT", new BigDecimal("25.0"));

            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(OrderFilledEvent.class);
        }

        @Test
        @DisplayName("같은 수수료 토큰이라면 수수료는 누적된다")
        void accumulatesFeesForSameToken() {
            tracker.startTrackingOrder(testInFlightOrder);

            TokenAmount fee = new TokenAmount("USDT", new BigDecimal("25.0"));
            TradeUpdateEvent fill_1 = createFill("T-1", "50000.0", "0.5", "25000.0", fee);
            TradeUpdateEvent fill_2 = createFill("T-2", "50000.0", "0.5", "25000.0", fee);

            tracker.processTradeUpdate(fill_1);
            tracker.processTradeUpdate(fill_2);

            InFlightOrder updated = tracker.getInFlightOrderByClientId("OID-123");
            assertThat(updated.getAccumulatedFees().get("USDT")).isEqualByComparingTo(new BigDecimal("50.0"));
        }

        @Test
        @DisplayName("완전 체결 TradeUpdate만으로는 CompletedEvent가 발생하지 않는다")
        void fullFillDoesNotTriggerCompleted() {
            tracker.startTrackingOrder(testInFlightOrder);

            TradeUpdateEvent fullFill = createFill("T-1", "50000.0", "1.0", "50000.0");
            tracker.processTradeUpdate(fullFill);

            assertThat(testInFlightOrder.isDone()).isTrue();
            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNotNull();

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
        @DisplayName("중복 tradeId는 무시되고 체결량이 중복 누적되지 않는다")
        void ignoresDuplicateTradeId() {
            tracker.startTrackingOrder(testInFlightOrder);
            TradeUpdateEvent fill = createFill("dup-trade", "50000.0", "0.5", "25000.0", null);
            tracker.processTradeUpdate(fill);
            tracker.processTradeUpdate(fill);
            assertThat(testInFlightOrder.getExecutedAmountBase()).isEqualByComparingTo(new BigDecimal("0.5"));
        }

        @Test
        @DisplayName("OPEN → 부분 체결 → 완전 체결 → FILLED 순서로 이벤트가 정확히 발생한다")
        void fullLifecycleFlow() {
            tracker.startTrackingOrder(testInFlightOrder);

            // OPEN
            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN));
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst()).isInstanceOf(BuyOrderCreatedEvent.class);

            // 부분 체결
            tracker.processTradeUpdate(createFill("T-1", "50000.0", "0.5", "25000.0"));
            assertThat(capturedEvents).hasSize(2);
            assertThat(capturedEvents.get(1)).isInstanceOf(OrderFilledEvent.class);

            tracker.processOrderUpdate(orderUpdate(OrderState.PARTIALLY_FILLED));
            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNotNull();

            // 완전 체결
            tracker.processTradeUpdate(createFill("T-2", "50000.0", "0.5", "25000.0"));
            assertThat(capturedEvents).hasSize(3);
            assertThat(capturedEvents.get(2)).isInstanceOf(OrderFilledEvent.class);

            // FILLED 상태 확정
            tracker.processOrderUpdate(orderUpdate(OrderState.FILLED));
            assertThat(capturedEvents).hasSize(4);
            assertThat(capturedEvents.get(3)).isInstanceOf(BuyOrderCompletedEvent.class);

            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNull();
        }

        @Test
        @DisplayName("TradeUpdate로 수량이 채워져도 OrderUpdateEvent(FILLED)가 와야 CompletedEvent가 발생한다")
        void completedRequiresBothFillAndStateUpdate() {
            tracker.startTrackingOrder(testInFlightOrder);
            tracker.processOrderUpdate(orderUpdate(OrderState.OPEN));
            capturedEvents.clear();

            // 수량은 다 채워짐
            tracker.processTradeUpdate(createFill("T-1", "50000.0", "1.0", "50000.0"));

            assertThat(testInFlightOrder.isDone()).isTrue();
            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNotNull();

            long completedCount = capturedEvents.stream()
                    .filter(e -> e instanceof BuyOrderCompletedEvent).count();
            assertThat(completedCount).isZero();

            // FILLED 상태 도착
            tracker.processOrderUpdate(orderUpdate(OrderState.FILLED));

            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNull();
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

            assertThat(tracker.getAll()).isEmpty();
        }

        @Test
        @DisplayName("미발견 횟수가 한계치 미만이면 트래킹을 유지한다")
        void belowLimit() {
            tracker.startTrackingOrder(testInFlightOrder);

            tracker.processOrderNotFound("OID-123");

            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNotNull();
        }

        @Test
        @DisplayName("미발견 횟수가 한계치를 초과하면 FAILED 처리 후 history")
        void exceedsLimit() {
            tracker.startTrackingOrder(testInFlightOrder);

            for(int i = 0; i < OrderTracker.LOST_ORDER_COUNT_LIMIT; i++) {
                tracker.processOrderNotFound("OID-123");
                tracker.processOrderNotFound("OID-123");
                tracker.processOrderNotFound("OID-123");
            }

            assertThat(tracker.getInFlightOrderByClientId("OID-123")).isNull();
            assertThat(testInFlightOrder.getCurrentState()).isEqualTo(OrderState.FAILED);
            verify(orderHistoryRepository).save(argThat(history ->
                    history.getClientOrderId().equals("OID-123") &&
                            history.isLost() &&
                            history.getTerminalState() == OrderState.FAILED
            ));
        }
    }
}