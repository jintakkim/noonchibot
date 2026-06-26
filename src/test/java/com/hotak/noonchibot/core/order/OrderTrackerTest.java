package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderTrackerTest {
    private static final String CLIENT_ORDER_ID = "OID-123";
    private static final String EXCHANGE_ORDER_ID = "EX-1";
    private static final String TRADING_PAIR = "BTC-USDT";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T00:00:05Z");

    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private OrderTracker tracker;
    private InFlightOrder order;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        tracker = new OrderTracker(
                eventPublisher,
                mock(TradeRepository.class),
                mock(OrderSnapshotRepository.class),
                eventSubscriber
        );
        order = newOrder(CLIENT_ORDER_ID, null);
    }

    @Nested
    @DisplayName("라이프사이클")
    class LifecycleTest {
        @Test
        @DisplayName("onStart 시 order/trade 이벤트를 구독한다")
        void onStartSubscribesEvents() {
            tracker.onStart();

            assertThat(eventSubscriber.isSubscribed(OrderEvent.StatusReceived.class)).isTrue();
            assertThat(eventSubscriber.isSubscribed(TradeEvent.Received.class)).isTrue();
            assertThat(tracker.phase()).isEqualTo(Phases.ORDER_TRACKER_SETUP);
        }

        @Test
        @DisplayName("onShutdown 시 모든 구독을 해제한다")
        void onShutdownClosesSubscriptions() {
            tracker.onStart();

            tracker.onShutdown();

            assertThat(eventSubscriber.count()).isZero();
        }
    }

    @Nested
    @DisplayName("트래킹 조회")
    class TrackingLookupTest {
        @Test
        @DisplayName("주문을 트래킹 목록에 추가하고 clientOrderId로 조회한다")
        void startTrackingAndFindByClientOrderId() {
            tracker.startTrackingOrder(order);

            assertThat(tracker.getInFlightOrderByClientId(CLIENT_ORDER_ID)).isSameAs(order);
            assertThat(tracker.getAllInFlightOrders()).containsExactly(order);
            assertThat(tracker.getOrderByClientId(CLIENT_ORDER_ID))
                    .map(OrderView::clientOrderId)
                    .hasValue(CLIENT_ORDER_ID);
        }

        @Test
        @DisplayName("exchangeOrderId로 트래킹 중인 주문을 조회한다")
        void findByExchangeOrderId() {
            InFlightOrder orderWithExchangeId = newOrder(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID);

            tracker.startTrackingOrder(orderWithExchangeId);

            assertThat(tracker.getInFlightOrderByExchangeId(EXCHANGE_ORDER_ID)).isSameAs(orderWithExchangeId);
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회는 null이나 Optional.empty를 반환한다")
        void findMissingOrder() {
            tracker.startTrackingOrder(order);

            assertThat(tracker.getInFlightOrder("missing", null)).isNull();
            assertThat(tracker.getInFlightOrder(null, "missing")).isNull();
            assertThat(tracker.getInFlightOrder(null, null)).isNull();
            assertThat(tracker.getOrderByClientId("missing")).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문 상태 업데이트")
    class OrderStatusUpdateTest {
        @Test
        @DisplayName("상태가 변경되면 주문을 갱신하고 snapshot update 요청을 발행한다")
        void stateChangedPublishesSnapshotUpdateRequested() {
            tracker.startTrackingOrder(order);

            tracker.processOrderUpdate(status(OrderState.OPEN));

            assertThat(order.getCurrentState()).isEqualTo(OrderState.OPEN);
            assertThat(order.getExchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(order.getLastUpdateTimestamp()).isEqualTo(UPDATED_AT);

            OrderEvent.SnapshotUpdateRequested event = eventPublisher.only(OrderEvent.SnapshotUpdateRequested.class);
            assertThat(event.clientOrderId()).isEqualTo(CLIENT_ORDER_ID);
            assertThat(event.exchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(event.orderState()).isEqualTo(OrderState.OPEN);
            assertThat(event.timestamp()).isEqualTo(UPDATED_AT);
        }

        @Test
        @DisplayName("상태가 변경되지 않으면 snapshot update 요청을 발행하지 않는다")
        void sameStateDoesNotPublishSnapshotUpdateRequested() {
            tracker.startTrackingOrder(order);

            tracker.processOrderUpdate(status(OrderState.PENDING_CREATE, CLIENT_ORDER_ID, EXCHANGE_ORDER_ID));

            assertThat(order.getExchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(eventPublisher.totalCount()).isZero();
        }

        @Test
        @DisplayName("terminal 상태가 되면 in-flight에서 제거하고 recent closed cache에서 조회된다")
        void terminalStateMovesToRecentClosedOrders() {
            tracker.startTrackingOrder(order);
            tracker.processOrderUpdate(status(OrderState.OPEN));
            eventPublisher.clear();

            tracker.processOrderUpdate(status(OrderState.FILLED));

            assertThat(tracker.getInFlightOrderByClientId(CLIENT_ORDER_ID)).isNull();
            OrderView view = tracker.getOrderByClientId(CLIENT_ORDER_ID).orElseThrow();
            assertThat(view.clientOrderId()).isEqualTo(CLIENT_ORDER_ID);
            assertThat(view.exchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
            assertThat(view.state()).isEqualTo(OrderState.FILLED);
            assertThat(eventPublisher.only(OrderEvent.SnapshotUpdateRequested.class).orderState()).isEqualTo(OrderState.FILLED);
        }

        @Test
        @DisplayName("트래킹 중인 주문을 찾지 못하면 예외를 던진다")
        void missingOrderThrows() {
            assertThatThrownBy(() -> tracker.processOrderUpdate(status(OrderState.OPEN)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(CLIENT_ORDER_ID);
        }

        @Test
        @DisplayName("ID가 일치하지 않는 상태 업데이트는 예외를 던진다")
        void mismatchedIdThrows() {
            tracker.startTrackingOrder(order);

            assertThatThrownBy(() -> tracker.processOrderUpdate(
                    status(OrderState.OPEN, "other-client", "other-exchange")
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("체결 업데이트")
    class TradeUpdateTest {
        @Test
        @DisplayName("체결 업데이트는 체결량과 수수료를 누적한다")
        void appliesTradeFill() {
            tracker.startTrackingOrder(order);

            tracker.processTradeUpdate(tradeReceived(fill("T-1", "0.4", "20000", "0.05")));

            assertThat(order.getExecutedAmountBase()).isEqualByComparingTo("0.4");
            assertThat(order.getExecutedAmountQuote()).isEqualByComparingTo("20000");
            assertThat(order.getAccumulatedFees()).containsEntry("USDT", new BigDecimal("0.05"));
            assertThat(order.getProcessedTradeIds()).containsExactly("T-1");
            assertThat(eventPublisher.totalCount()).isZero();
        }

        @Test
        @DisplayName("중복 tradeId는 무시한다")
        void ignoresDuplicateTradeId() {
            tracker.startTrackingOrder(order);
            TradeEvent.Received trade = tradeReceived(fill("T-1", "0.4", "20000", "0.05"));

            tracker.processTradeUpdate(trade);
            tracker.processTradeUpdate(trade);

            assertThat(order.getExecutedAmountBase()).isEqualByComparingTo("0.4");
            assertThat(order.getAccumulatedFees()).containsEntry("USDT", new BigDecimal("0.05"));
        }

        @Test
        @DisplayName("exchangeOrderId만으로도 체결 업데이트를 적용한다")
        void appliesTradeByExchangeOrderId() {
            InFlightOrder orderWithExchangeId = newOrder(CLIENT_ORDER_ID, EXCHANGE_ORDER_ID);
            tracker.startTrackingOrder(orderWithExchangeId);

            tracker.processTradeUpdate(new TradeEvent.Received(
                    null,
                    EXCHANGE_ORDER_ID,
                    TRADING_PAIR,
                    List.of(fill("T-1", "0.4", "20000", "0.05"))
            ));

            assertThat(orderWithExchangeId.getExecutedAmountBase()).isEqualByComparingTo("0.4");
        }

        @Test
        @DisplayName("clientOrderId와 exchangeOrderId가 모두 없으면 예외를 던진다")
        void tradeWithoutIdsThrows() {
            assertThatThrownBy(() -> tracker.processTradeUpdate(new TradeEvent.Received(
                    null,
                    null,
                    TRADING_PAIR,
                    List.of(fill("T-1", "0.4", "20000", "0.05"))
            ))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("트래킹 중이 아닌 주문의 체결 업데이트는 무시한다")
        void missingOrderTradeIgnored() {
            tracker.processTradeUpdate(tradeReceived(fill("T-1", "0.4", "20000", "0.05")));

            assertThat(eventPublisher.totalCount()).isZero();
            assertThat(tracker.getAllInFlightOrders()).isEmpty();
        }

        @Test
        @DisplayName("체결량이 전부 채워져도 상태 업데이트 전에는 in-flight에서 제거하지 않는다")
        void fullTradeFillDoesNotRemoveOrderWithoutTerminalStatus() {
            tracker.startTrackingOrder(order);

            tracker.processTradeUpdate(tradeReceived(fill("T-1", "1.0", "50000", "0.1")));

            assertThat(order.isDone()).isTrue();
            assertThat(tracker.getInFlightOrderByClientId(CLIENT_ORDER_ID)).isSameAs(order);
            assertThat(tracker.getOrderByClientId(CLIENT_ORDER_ID)).isPresent();
        }
    }

    private InFlightOrder newOrder(String clientOrderId, String exchangeOrderId) {
        return new InFlightOrder(
                clientOrderId,
                TRADING_PAIR,
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("1.0"),
                new BigDecimal("50000"),
                CREATED_AT,
                exchangeOrderId,
                false,
                TimeInForce.GTC,
                new HashSet<>(),
                new HashMap<>()
        );
    }

    private OrderEvent.StatusReceived status(OrderState state) {
        return status(state, CLIENT_ORDER_ID, EXCHANGE_ORDER_ID);
    }

    private OrderEvent.StatusReceived status(OrderState state, String clientOrderId, String exchangeOrderId) {
        return new OrderEvent.StatusReceived(
                TRADING_PAIR,
                clientOrderId,
                exchangeOrderId,
                state,
                UPDATED_AT
        );
    }

    private TradeEvent.Received tradeReceived(TradeEvent.Fill fill) {
        return new TradeEvent.Received(
                CLIENT_ORDER_ID,
                EXCHANGE_ORDER_ID,
                TRADING_PAIR,
                List.of(fill)
        );
    }

    private TradeEvent.Fill fill(String tradeId, String baseAmount, String quoteAmount, String feeAmount) {
        return new TradeEvent.Fill(
                tradeId,
                UPDATED_AT,
                new BigDecimal("50000"),
                new BigDecimal(baseAmount),
                new BigDecimal(quoteAmount),
                new TokenAmount("USDT", new BigDecimal(feeAmount)),
                true
        );
    }
}
