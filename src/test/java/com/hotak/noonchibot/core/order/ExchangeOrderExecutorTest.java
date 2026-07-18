package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.OrderIdGenerator;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeOperationSucceededEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.exchange.ExchangeOperation;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeType;
import com.hotak.noonchibot.core.trade.TradingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExchangeOrderExecutorTest {
    private static final Exchange EXCHANGE = Exchange.BINANCE_DERIVATIVE;

    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private OrderIdGenerator orderIdGenerator;
    private OrderTracker orderTracker;
    private OrderClient orderClient;
    private OrderBookTracker orderBookTracker;
    private TradingRuleRegistry tradingRuleRegistry;
    private OrderSnapshotRepository orderSnapshotRepository;
    private ExchangeOrderExecutor executor;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        orderIdGenerator = mock(OrderIdGenerator.class);
        orderTracker = mock(OrderTracker.class);
        orderClient = mock(OrderClient.class);
        orderBookTracker = mock(OrderBookTracker.class);
        orderSnapshotRepository = mock(OrderSnapshotRepository.class);
        tradingRuleRegistry = tradingPair -> new TradingRule(
                tradingPair,
                new BigDecimal("0.01"),
                null,
                new BigDecimal("0.01"),
                new BigDecimal("0.00001"),
                new BigDecimal("5"),
                8,
                Set.of(OrderType.LIMIT, OrderType.MARKET),
                "USDT",
                "BTC"
        );
        executor = new ExchangeOrderExecutor(
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                "NB",
                36,
                Set.of(TimeInForce.GTC, TimeInForce.IOC, TimeInForce.FOK),
                orderBookTracker,
                eventPublisher,
                orderClient,
                eventSubscriber,
                EXCHANGE,
                orderSnapshotRepository
        );
    }

    @Test
    @DisplayName("onStart 시 주문 생성/취소 이벤트를 구독한다")
    void onStartSubscribesOrderEvents() {
        executor.onStart();

        assertThat(eventSubscriber.isSubscribed(OrderEvent.CreateRequested.class)).isTrue();
        assertThat(eventSubscriber.isSubscribed(OrderEvent.ExchangeCreateRequested.class)).isTrue();
        assertThat(eventSubscriber.isSubscribed(OrderEvent.CancelRequested.class)).isTrue();
        assertThat(eventSubscriber.isSubscribed(OrderEvent.ExchangeCancelRequested.class)).isTrue();
        assertThat(executor.phase()).isEqualTo(Phases.ORDER_EXECUTOR_SETUP);
    }

    @Test
    @DisplayName("onShutdown 시 구독을 해제한다")
    void onShutdownClosesSubscriptions() {
        executor.onStart();

        executor.onShutdown();

        assertThat(eventSubscriber.count()).isZero();
    }

    @Test
    @DisplayName("clientOrderId를 생성할 때 prefix와 길이 제한을 위임한다")
    void createClientOrderIdDelegatesToGenerator() {
        when(orderIdGenerator.createClientOrderId(true, "BTC-USDT", "NB", 36)).thenReturn("generated-id");

        String clientOrderId = executor.createClientOrderId(true, "BTC-USDT");

        assertThat(clientOrderId).isEqualTo("generated-id");
    }

    @Test
    @DisplayName("주문 생성 요청을 검증하고 수량/가격을 보정한 뒤 거래소 주문 생성 요청 이벤트를 발행한다")
    void createRequested_publishesExchangeCreateRequestedWithQuantizedOrder() {
        executor.processCreateRequest(new OrderEvent.CreateRequested(
                limitBuy("0.019999", "50000.123"),
                "cid-1",
                EXCHANGE,
                "strategy-1",
                "group-1"
        ));

        OrderEvent.ExchangeCreateRequested event = eventPublisher.only(OrderEvent.ExchangeCreateRequested.class);
        InFlightOrder order = event.inFlightOrder();

        assertThat(order.getClientOrderId()).isEqualTo("cid-1");
        assertThat(order.getTradingPair()).isEqualTo("BTC-USDT");
        assertThat(order.getAmount()).isEqualByComparingTo("0.01999");
        assertThat(order.getPrice()).isEqualByComparingTo("50000.12");
        assertThat(event.strategyId()).isEqualTo("strategy-1");
        assertThat(event.executionGroupId()).isEqualTo("group-1");
        verify(orderTracker).startTrackingOrder(order);
    }

    @Test
    @DisplayName("거래소가 지정된 생성 요청은 해당 거래소 executor만 처리한다")
    void createRequested_whenExchangeDoesNotMatch_ignoresRequest() {
        executor.processCreateRequest(new OrderEvent.CreateRequested(
                limitBuy("0.019999", "50000.123"),
                "cid-1",
                Exchange.HYPERLIQUID_DERIVATIVE
        ));

        assertThat(eventPublisher.totalCount()).isZero();
        verifyNoInteractions(orderTracker);
    }

    @Test
    @DisplayName("전략 주문처럼 clientOrderId가 없으면 executor가 생성한다")
    void createRequested_withoutClientOrderId_generatesClientOrderId() {
        when(orderIdGenerator.createClientOrderId(true, "BTC-USDT", "NB", 36)).thenReturn("generated-id");

        executor.processCreateRequest(new OrderEvent.CreateRequested(
                limitBuy("0.019999", "50000.123"),
                null,
                EXCHANGE
        ));

        OrderEvent.ExchangeCreateRequested event = eventPublisher.only(OrderEvent.ExchangeCreateRequested.class);
        assertThat(event.inFlightOrder().getClientOrderId()).isEqualTo("generated-id");
    }

    @Test
    @DisplayName("주문 생성 검증에 실패하면 실패 이벤트를 발행한다")
    void createRequested_whenValidationFails_publishesFailedEvent() {
        executor.processCreateRequest(new OrderEvent.CreateRequested(
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
    @DisplayName("검증된 주문의 추적 시작에 실패하면 주문 실패 이벤트를 발행한다")
    void createRequested_whenTrackerFails_publishesOrderFailure() {
        IllegalStateException cause = new IllegalStateException("tracker unavailable");
        doThrow(cause).when(orderTracker).startTrackingOrder(any(InFlightOrder.class));

        executor.processCreateRequest(new OrderEvent.CreateRequested(
                limitBuy("0.019999", "50000.123"),
                "cid-1",
                EXCHANGE,
                "strategy-1",
                "group-1"
        ));

        OrderEvent.Failed failed = eventPublisher.only(OrderEvent.Failed.class);
        assertThat(failed.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(failed.clientOrderId()).isEqualTo("cid-1");
        assertThat(failed.throwable()).isSameAs(cause);
        assertThat(eventPublisher.hasEventOfType(ExchangeFailureEvent.class)).isFalse();
    }

    @Test
    @DisplayName("거래소 주문 생성 전 snapshot을 저장하고 성공하면 상태와 작업 성공 이벤트를 발행한다")
    void exchangeCreateRequested_whenOrderClientSucceeds_savesSnapshotAndPublishesStatusReceived() {
        InFlightOrder order = inFlightOrder("cid-1", null);
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");
        when(orderClient.placeOrder(order))
                .thenReturn(new OrderPlaceResult("ex-1", OrderState.OPEN, timestamp));

        executor.processExchangeCreateRequest(new OrderEvent.ExchangeCreateRequested(
                order,
                "strategy-1",
                "group-1"
        ));

        verify(orderSnapshotRepository).save(any(OrderSnapshot.class));
        OrderEvent.StatusReceived status = eventPublisher.only(OrderEvent.StatusReceived.class);
        assertThat(status.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(status.clientOrderId()).isEqualTo("cid-1");
        assertThat(status.exchangeOrderId()).isEqualTo("ex-1");
        assertThat(status.orderState()).isEqualTo(OrderState.OPEN);
        assertThat(status.timestamp()).isEqualTo(timestamp);
        ExchangeOperationSucceededEvent succeeded =
                eventPublisher.only(ExchangeOperationSucceededEvent.class);
        assertThat(succeeded.exchange()).isEqualTo(EXCHANGE);
        assertThat(succeeded.operation()).isEqualTo(ExchangeOperation.ORDER_PLACE);
        assertThat(succeeded.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("거래소 주문 생성 실패 시 전략 문맥을 포함한 거래소 실패 이벤트를 발행한다")
    void exchangeCreateRequested_whenOrderClientFails_publishesExchangeFailure() {
        InFlightOrder order = inFlightOrder("cid-1", null);
        IllegalStateException cause = new IllegalStateException("boom");
        when(orderClient.placeOrder(order)).thenThrow(cause);

        executor.processExchangeCreateRequest(new OrderEvent.ExchangeCreateRequested(
                order,
                "strategy-1",
                "group-1"
        ));

        ExchangeFailureEvent failed = eventPublisher.only(ExchangeFailureEvent.class);
        assertThat(failed.exchange()).isEqualTo(EXCHANGE);
        assertThat(failed.operation()).isEqualTo(ExchangeOperation.ORDER_PLACE);
        assertThat(failed.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(failed.clientOrderId()).isEqualTo("cid-1");
        assertThat(failed.exchangeOrderId()).isNull();
        assertThat(failed.strategyId()).isEqualTo("strategy-1");
        assertThat(failed.executionGroupId()).isEqualTo("group-1");
        assertThat(failed.cause()).isSameAs(cause);
        assertThat(failed.occurredAt()).isNotNull();
        assertThat(eventPublisher.hasEventOfType(OrderEvent.Failed.class)).isFalse();
    }

    @Test
    @DisplayName("초기 snapshot 저장 실패는 거래소 실패가 아닌 주문 실패 이벤트로 발행한다")
    void exchangeCreateRequested_whenInitialSnapshotFails_publishesOrderFailure() {
        InFlightOrder order = inFlightOrder("cid-1", null);
        IllegalStateException cause = new IllegalStateException("snapshot unavailable");
        when(orderSnapshotRepository.save(any(OrderSnapshot.class))).thenThrow(cause);

        executor.processExchangeCreateRequest(new OrderEvent.ExchangeCreateRequested(
                order,
                "strategy-1",
                "group-1"
        ));

        OrderEvent.Failed failed = eventPublisher.only(OrderEvent.Failed.class);
        assertThat(failed.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(failed.clientOrderId()).isEqualTo("cid-1");
        assertThat(failed.throwable()).isSameAs(cause);
        assertThat(eventPublisher.hasEventOfType(ExchangeFailureEvent.class)).isFalse();
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("취소 요청을 받으면 추적 중인 주문을 찾아 거래소 취소 요청 이벤트를 발행한다")
    void cancelRequested_whenOrderIsTracked_publishesExchangeCancelRequested() {
        InFlightOrder order = inFlightOrder("cid-1", "ex-1");
        when(orderTracker.getInFlightOrderByClientId("cid-1")).thenReturn(order);

        executor.processCancelRequest(new OrderEvent.CancelRequested(
                "cid-1",
                EXCHANGE,
                "strategy-1"
        ));

        OrderEvent.ExchangeCancelRequested request = eventPublisher.only(OrderEvent.ExchangeCancelRequested.class);
        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class).orderState())
                .isEqualTo(OrderState.PENDING_CANCEL);
        assertThat(request.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(request.clientOrderId()).isEqualTo("cid-1");
        assertThat(request.exchangeOrderId()).isEqualTo("ex-1");
        assertThat(request.strategyId()).isEqualTo("strategy-1");
    }

    @Test
    @DisplayName("거래소가 지정된 취소 요청은 해당 거래소 executor만 처리한다")
    void cancelRequested_whenExchangeDoesNotMatch_ignoresRequest() {
        executor.processCancelRequest(new OrderEvent.CancelRequested(
                "cid-1",
                Exchange.HYPERLIQUID_DERIVATIVE
        ));

        assertThat(eventPublisher.totalCount()).isZero();
        verifyNoInteractions(orderTracker);
    }

    @Test
    @DisplayName("취소할 주문을 찾지 못하면 failed 이벤트를 발행한다")
    void cancelRequested_whenOrderIsMissing_publishesFailed() {
        when(orderTracker.getInFlightOrderByClientId("cid-1")).thenReturn(null);

        executor.processCancelRequest(new OrderEvent.CancelRequested("cid-1"));

        OrderEvent.Failed failed = eventPublisher.only(OrderEvent.Failed.class);
        assertThat(failed.clientOrderId()).isEqualTo("cid-1");
        assertThat(failed.throwable()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("거래소 취소가 확정되면 CANCELED 상태와 작업 성공 이벤트를 발행한다")
    void exchangeCancelRequested_whenCancelIsFinalized_publishesCanceledStatus() {
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");
        when(orderClient.cancelOrder("BTC-USDT", "cid-1"))
                .thenReturn(new OrderCancelResult(true, timestamp));

        executor.processExchangeCancelRequest(new OrderEvent.ExchangeCancelRequested("BTC-USDT", "cid-1", "ex-1"));

        OrderEvent.StatusReceived status = eventPublisher.only(OrderEvent.StatusReceived.class);
        assertThat(status.orderState()).isEqualTo(OrderState.CANCELED);
        assertThat(status.timestamp()).isEqualTo(timestamp);
        ExchangeOperationSucceededEvent succeeded =
                eventPublisher.only(ExchangeOperationSucceededEvent.class);
        assertThat(succeeded.exchange()).isEqualTo(EXCHANGE);
        assertThat(succeeded.operation()).isEqualTo(ExchangeOperation.ORDER_CANCEL);
        assertThat(succeeded.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("거래소 취소가 아직 확정되지 않았으면 PENDING_CANCEL 상태 수신 이벤트를 발행한다")
    void exchangeCancelRequested_whenCancelIsNotFinalized_publishesPendingCancelStatus() {
        when(orderClient.cancelOrder("BTC-USDT", "cid-1"))
                .thenReturn(new OrderCancelResult(false, Instant.parse("2026-06-01T00:00:00Z")));

        executor.processExchangeCancelRequest(new OrderEvent.ExchangeCancelRequested("BTC-USDT", "cid-1", "ex-1"));

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class).orderState()).isEqualTo(OrderState.PENDING_CANCEL);
    }

    @Test
    @DisplayName("거래소 주문 취소 실패 시 전략 문맥을 포함한 거래소 실패 이벤트를 발행한다")
    void exchangeCancelRequested_whenOrderClientFails_publishesExchangeFailure() {
        IllegalStateException cause = new IllegalStateException("boom");
        when(orderClient.cancelOrder("BTC-USDT", "cid-1")).thenThrow(cause);

        executor.processExchangeCancelRequest(new OrderEvent.ExchangeCancelRequested(
                "BTC-USDT",
                "cid-1",
                "ex-1",
                "strategy-1"
        ));

        ExchangeFailureEvent failed = eventPublisher.only(ExchangeFailureEvent.class);
        assertThat(failed.exchange()).isEqualTo(EXCHANGE);
        assertThat(failed.operation()).isEqualTo(ExchangeOperation.ORDER_CANCEL);
        assertThat(failed.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(failed.clientOrderId()).isEqualTo("cid-1");
        assertThat(failed.exchangeOrderId()).isEqualTo("ex-1");
        assertThat(failed.strategyId()).isEqualTo("strategy-1");
        assertThat(failed.executionGroupId()).isNull();
        assertThat(failed.cause()).isSameAs(cause);
        assertThat(failed.occurredAt()).isNotNull();
        assertThat(eventPublisher.hasEventOfType(OrderEvent.Failed.class)).isFalse();
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
