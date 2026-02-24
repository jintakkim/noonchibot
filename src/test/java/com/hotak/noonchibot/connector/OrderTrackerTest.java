package com.hotak.noonchibot.connector;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.hotak.noonchibot.connector.AbstractConnector;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.*; // 관련 이벤트 클래스들
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

class OrderTrackerTest {

    private OrderTracker tracker;
    private AbstractConnector mockConnector;
    private InFlightOrder testOrder;

    // 추상 클래스인 EventLogger 대신 구현체인 SimpleEventLogger를 사용합니다.
    private SimpleEventLogger buyOrderCompletedLogger;
    private SimpleEventLogger buyOrderCreatedLogger;
    private SimpleEventLogger orderCancelledLogger;
    private SimpleEventLogger orderFailureLogger;
    private SimpleEventLogger orderFilledLogger;
    private SimpleEventLogger sellOrderCompletedLogger;
    private SimpleEventLogger sellOrderCreatedLogger;

    @BeforeEach
    void setUp() {
        mockConnector = mock(AbstractConnector.class);
        tracker = new OrderTracker(mockConnector, 2);
        initializeEventLoggers();
        testOrder = createTestOrder("OID-123");
    }

    private void initializeEventLoggers() {
        buyOrderCompletedLogger = new SimpleEventLogger("BuyOrderCompleted");
        buyOrderCreatedLogger = new SimpleEventLogger("BuyOrderCreated");
        orderCancelledLogger = new SimpleEventLogger("OrderCancelled");
        orderFailureLogger = new SimpleEventLogger("OrderFailure");
        orderFilledLogger = new SimpleEventLogger("OrderFilled");
        sellOrderCompletedLogger = new SimpleEventLogger("SellOrderCompleted");
        sellOrderCreatedLogger = new SimpleEventLogger("SellOrderCreated");

        // 메서드 참조(::)를 사용하여 타입을 맞춰줍니다.
        // buyOrderCompletedLogger::onEvent는 EventListener<BuyOrderCompletedEvent>의 역할을 완벽히 수행합니다.
        mockConnector.addListener(BuyOrderCompletedEvent.class, buyOrderCompletedLogger::onEvent);
        mockConnector.addListener(BuyOrderCreatedEvent.class, buyOrderCreatedLogger::onEvent);
        mockConnector.addListener(OrderCancelledEvent.class, orderCancelledLogger::onEvent);
        mockConnector.addListener(OrderFailureEvent.class, orderFailureLogger::onEvent);
        mockConnector.addListener(OrderFilledEvent.class, orderFilledLogger::onEvent);
        mockConnector.addListener(SellOrderCompletedEvent.class, sellOrderCompletedLogger::onEvent);
        mockConnector.addListener(SellOrderCreatedEvent.class, sellOrderCreatedLogger::onEvent);
    }

    private InFlightOrder createTestOrder(String id) {
        return new InFlightOrder(
                id, "BTC-USDT", OrderType.LIMIT, TradeType.BUY,
                new BigDecimal("1.0"), new BigDecimal("50000.0"), Instant.now()
        );
    }

    @Test
    @DisplayName("주문을 트래킹 목록에 추가하면 조회가 가능해야 한다")
    void testStartTrackingOrder() {
        assertThat(tracker.getAllOrders()).isEmpty();

        tracker.startTrackingOrder(testOrder);

        assertThat(tracker.getAllOrders()).hasSize(1);

        InFlightOrder fetchedOrder = tracker.fetchTrackedOrder(testOrder.getClientOrderId());
        assertThat(fetchedOrder).isEqualTo(testOrder);
        assertThat(fetchedOrder.getClientOrderId()).isEqualTo("OID-123");
    }
}