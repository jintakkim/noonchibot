package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradePollerTest {
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler taskScheduler;
    private OrderTracker orderTracker;
    private TradePoller poller;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        taskScheduler = new TestTaskScheduler();
        orderTracker = mock(OrderTracker.class);
        poller = new TradePoller(eventPublisher, taskScheduler, orderTracker);
    }

    @Test
    @DisplayName("poll 시 모든 inFlightOrder에 대해 trade update 요청 이벤트를 발행한다")
    void poll_publishesUpdateRequestsForAllInFlightOrders() {
        InFlightOrder firstOrder = order("cid-1", "eid-1", "BTC-USDT");
        InFlightOrder secondOrder = order("cid-2", "eid-2", "ETH-USDT");
        when(orderTracker.getAllInFlightOrders()).thenReturn(List.of(firstOrder, secondOrder));

        poller.poll();

        assertThat(eventPublisher.getEventsOfType(TradeEvent.UpdateRequested.class))
                .containsExactly(
                        new TradeEvent.UpdateRequested("cid-1", "eid-1", "BTC-USDT"),
                        new TradeEvent.UpdateRequested("cid-2", "eid-2", "ETH-USDT")
                );
    }

    @Test
    @DisplayName("시작 시 1분 fixed delay task를 등록한다")
    void onStart_registersFixedDelayTask() {
        poller.onStart();

        assertThat(taskScheduler.onlyScheduledTask().kind())
                .isEqualTo(TestTaskScheduler.ScheduleKind.FIXED_DELAY);
        assertThat(taskScheduler.onlyScheduledTask().delay())
                .isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("종료 시 등록된 task를 취소한다")
    void onShutdown_cancelsScheduledTask() {
        poller.onStart();

        poller.onShutdown();

        assertThat(taskScheduler.onlyScheduledTask().isCancelled()).isTrue();
    }

    @Test
    @DisplayName("phase는 TRADE_POLLING이다")
    void phase_returnsTradePollingPhase() {
        assertThat(poller.phase()).isEqualTo(Phases.TRADE_POLLING);
    }

    private InFlightOrder order(String clientOrderId, String exchangeOrderId, String tradingPair) {
        return new InFlightOrder(
                clientOrderId,
                tradingPair,
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("1.0"),
                new BigDecimal("100.0"),
                Instant.parse("2026-06-22T00:00:00Z"),
                exchangeOrderId,
                false,
                TimeInForce.GTC,
                java.util.Set.of(),
                java.util.Map.of()
        );
    }
}
