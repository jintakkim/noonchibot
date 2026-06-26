package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderStatusPollerTest {
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler taskScheduler;
    private OrderTracker orderTracker;
    private OrderStatusPoller poller;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        taskScheduler = new TestTaskScheduler();
        orderTracker = mock(OrderTracker.class);
        poller = new OrderStatusPoller(eventPublisher, orderTracker, taskScheduler);
    }

    @Test
    @DisplayName("시작 시 30초 fixed delay task를 등록하고 task는 상태 조회 요청을 발행한다")
    void onStart_registersTaskAndPublishesStatusRequests() {
        when(orderTracker.getAllInFlightOrders()).thenReturn(List.of(order("cid-1", "eid-1", "BTC-USDT")));

        poller.onStart();
        taskScheduler.onlyScheduledTask().task().run();

        assertThat(taskScheduler.onlyScheduledTask().delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(eventPublisher.only(OrderEvent.StatusUpdateRequested.class))
                .isEqualTo(new OrderEvent.StatusUpdateRequested("BTC-USDT", "cid-1"));
    }

    @Test
    @DisplayName("종료 시 등록된 task를 취소한다")
    void onShutdown_cancelsScheduledTask() {
        poller.onStart();

        poller.onShutdown();

        assertThat(taskScheduler.onlyScheduledTask().isCancelled()).isTrue();
    }

    @Test
    @DisplayName("phase는 ORDER_STATUS_POLLING이다")
    void phase_returnsOrderStatusPollingPhase() {
        assertThat(poller.phase()).isEqualTo(Phases.ORDER_STATUS_POLLING);
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
                Set.of(),
                Map.of()
        );
    }
}
