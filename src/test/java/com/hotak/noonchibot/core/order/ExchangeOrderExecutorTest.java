package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.trade.TradingRule;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

public class ExchangeOrderExecutorTest {
    private static final EventMetadata METADATA = EventMetadata.newRoot();
    private static final Exchange EXCHANGE = Exchange.BINANCE_DERIVATIVE;

    private TestEventPublisher eventPublisher;
    private OrderTracker orderTracker;
    private OrderClient orderClient;
    private OrderBookTracker orderBookTracker;
    private TradingRuleRegistry tradingRuleRegistry;
    private OrderSnapshotRepository orderSnapshotRepository;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        orderTracker = mock(OrderTracker.class);
        orderClient = mock(OrderClient.class);
        orderBookTracker = mock(OrderBookTracker.class);
        orderSnapshotRepository = mock(OrderSnapshotRepository.class);
        tradingRuleRegistry = tradingPair -> new TradingRule(
                tradingPair,
                new BigDecimal("0.00001"),
                null,
                new BigDecimal("0.01"),
                new BigDecimal("0.00001"),
                new BigDecimal("5"),
                8,
                Set.of(OrderType.LIMIT, OrderType.MARKET),
                "USDT",
                "BTC"
        );
    }

    @Test
    @DisplayName("주문 생성 요청을 검증하고 수량/가격을 보정한 뒤 거래소 주문 생성 요청 이벤트를 발행한다")
    void createRequested_publishesExchangeCreateRequestedWithQuantizedOrder() {
        var handler = createRequestHandler();

        handle(handler, new OrderEvent.CreateRequested(
                limitBuy("0.019999", "50000.123"),
                "cid-1"
        ));

        OrderEvent.ExchangeCreateRequested event = eventPublisher.only(OrderEvent.ExchangeCreateRequested.class);
        InFlightOrder order = event.inFlightOrder();

        assertThat(order.getClientOrderId()).isEqualTo("cid-1");
        assertThat(order.getTradingPair()).isEqualTo("BTC-USDT");
        assertThat(order.getAmount()).isEqualByComparingTo("0.01999");
        assertThat(order.getPrice()).isEqualByComparingTo("50000.12");
        verify(orderTracker).startTrackingOrder(order);
    }

    @Test
    @DisplayName("주문 생성 검증에 실패하면 실패 이벤트를 발행한다")
    void createRequested_whenValidationFails_publishesFailedEvent() {
        var handler = createRequestHandler();

        handle(handler, new OrderEvent.CreateRequested(
                limitBuy("0.000001", "50000"),
                "cid-1"
        ));

        OrderEvent.Failed failed = eventPublisher.only(OrderEvent.Failed.class);

        assertThat(failed.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(failed.clientOrderId()).isEqualTo("cid-1");
        assertThat(failed.exchangeOrderId()).isNull();
        assertThat(failed.throwable())
                .isInstanceOf(OrderValidationException.BelowMinOrderSizeException.class);
    }

    @Test
    @DisplayName("거래소 주문 생성 요청 전에 주문 스냅샷을 저장하고, 성공하면 주문 상태 수신 이벤트를 발행한다")
    void exchangeCreateRequested_whenOrderClientSucceeds_savesSnapshotAndPublishesStatusReceived() {
        var handler = new ExchangeOrderExecutor.ExchangeCreateRequestHandler(
                eventPublisher,
                orderClient,
                orderSnapshotRepository,
                EXCHANGE
        );
        InFlightOrder order = inFlightOrder("cid-1", null);
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");

        when(orderClient.placeOrder(order))
                .thenReturn(new OrderPlaceResult("ex-1", OrderState.OPEN, timestamp));

        handle(handler, new OrderEvent.ExchangeCreateRequested(order));

        verify(orderSnapshotRepository).save(any(OrderSnapshot.class));

        OrderEvent.StatusReceived status = eventPublisher.only(OrderEvent.StatusReceived.class);
        assertThat(status.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(status.clientOrderId()).isEqualTo("cid-1");
        assertThat(status.exchangeOrderId()).isEqualTo("ex-1");
        assertThat(status.orderState()).isEqualTo(OrderState.OPEN);
        assertThat(status.timestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("취소 요청을 받으면 추적 중인 주문을 찾아 거래소 취소 요청 이벤트를 발행한다")
    void cancelRequested_whenOrderIsTracked_publishesExchangeCancelRequested() {
        var handler = new ExchangeOrderExecutor.CancelRequestHandler(eventPublisher, orderTracker);
        InFlightOrder order = inFlightOrder("cid-1", "ex-1");

        when(orderTracker.getInFlightOrderByClientId("cid-1")).thenReturn(order);

        handle(handler, new OrderEvent.CancelRequested("cid-1"));

        OrderEvent.ExchangeCancelRequested request = eventPublisher.only(OrderEvent.ExchangeCancelRequested.class);

        assertThat(request.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(request.clientOrderId()).isEqualTo("cid-1");
        assertThat(request.exchangeOrderId()).isEqualTo("ex-1");
    }

    @Test
    @DisplayName("거래소 취소가 확정되면 CANCELED 상태 수신 이벤트를 발행한다")
    void exchangeCancelRequested_whenCancelIsFinalized_publishesCanceledStatus() {
        var handler = new ExchangeOrderExecutor.ExchangeCancelRequestHandler(eventPublisher, orderClient);
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");

        when(orderClient.cancelOrder("BTC-USDT", "cid-1"))
                .thenReturn(new OrderCancelResult(true, timestamp));

        handle(handler, new OrderEvent.ExchangeCancelRequested("BTC-USDT", "cid-1", "ex-1"));

        OrderEvent.StatusReceived status = eventPublisher.only(OrderEvent.StatusReceived.class);

        assertThat(status.orderState()).isEqualTo(OrderState.CANCELED);
        assertThat(status.timestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("거래소 취소가 아직 확정되지 않았으면 PENDING_CANCEL 상태 수신 이벤트를 발행한다")
    void exchangeCancelRequested_whenCancelIsNotFinalized_publishesPendingCancelStatus() {
        var handler = new ExchangeOrderExecutor.ExchangeCancelRequestHandler(eventPublisher, orderClient);
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");

        when(orderClient.cancelOrder("BTC-USDT", "cid-1"))
                .thenReturn(new OrderCancelResult(false, timestamp));

        handle(handler, new OrderEvent.ExchangeCancelRequested("BTC-USDT", "cid-1", "ex-1"));

        OrderEvent.StatusReceived status = eventPublisher.only(OrderEvent.StatusReceived.class);

        assertThat(status.orderState()).isEqualTo(OrderState.PENDING_CANCEL);
    }

    @Test
    @DisplayName("스냅샷 업데이트 요청을 받으면 기존 주문 스냅샷에 거래소 상태를 반영한다")
    void snapshotUpdateRequested_updatesOrderSnapshot() {
        var handler = new ExchangeOrderExecutor.SnapshotUpdateHandler(orderSnapshotRepository);
        OrderSnapshot snapshot = new OrderSnapshot(
                "cid-1",
                EXCHANGE,
                "BTC-USDT",
                OrderState.PENDING_CREATE,
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                null
        );
        Instant timestamp = Instant.parse("2026-06-01T00:01:00Z");

        when(orderSnapshotRepository.findById("cid-1"))
                .thenReturn(Optional.of(snapshot));

        handle(handler, new OrderEvent.SnapshotUpdateRequested(
                "cid-1",
                "ex-1",
                OrderState.OPEN,
                timestamp
        ));

        verify(orderSnapshotRepository).save(snapshot);

        assertThat(snapshot)
                .extracting("exchangeOrderId", "state", "updatedAt")
                .containsExactly("ex-1", OrderState.OPEN, timestamp);
    }

    private ExchangeOrderExecutor.CreateRequestHandler createRequestHandler() {
        return new ExchangeOrderExecutor.CreateRequestHandler(
                eventPublisher,
                Set.of(TimeInForce.GTC, TimeInForce.IOC, TimeInForce.FOK),
                orderBookTracker,
                tradingRuleRegistry,
                orderTracker
        );
    }

    private <E extends Event> void handle(FailureAwareEventHandler<E> handler, E event) {
        try {
            handler.onEvent(event, METADATA);
        } catch (Throwable cause) {
            handler.onFailure(event, cause);
        }
    }

    private OrderCandidate limitBuy(String amount, String price) {
        return OrderCandidate.builder()
                .tradingPair("BTC-USDT")
                .orderType(OrderType.LIMIT)
                .tradeType(TradeType.BUY)
                .amount(new BigDecimal(amount))
                .price(new BigDecimal(price))
                .timeInForce(TimeInForce.GTC)
                .build();
    }

    private InFlightOrder inFlightOrder(String clientOrderId, String exchangeOrderId) {
        return new InFlightOrder(
                clientOrderId,
                "BTC-USDT",
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("0.01"),
                new BigDecimal("50000"),
                Instant.parse("2026-06-01T00:00:00Z"),
                exchangeOrderId,
                false,
                TimeInForce.GTC,
                Set.of(),
                new java.util.HashMap<>()
        );
    }
}