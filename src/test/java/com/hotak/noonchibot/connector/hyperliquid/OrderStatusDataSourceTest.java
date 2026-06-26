package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private OrderStatusDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        dataSource = new OrderStatusDataSource(restAssistant, eventPublisher, new TestEventSubscriber(), HyperliquidFixture.USER);
    }

    @Test
    @DisplayName("orderStatus 응답을 StatusReceived 이벤트로 발행한다")
    void onEvent_publishesStatusReceived() {
        OrderEvent.StatusUpdateRequested request = new OrderEvent.StatusUpdateRequested(
                HyperliquidFixture.TRADING_PAIR,
                HyperliquidFixture.CLIENT_ORDER_ID
        );

        runWith(HyperliquidFixture.orderStatusSuccess(), () -> dataSource.onEvent(request));

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class))
                .isEqualTo(new OrderEvent.StatusReceived(
                        HyperliquidFixture.TRADING_PAIR,
                        HyperliquidFixture.CLIENT_ORDER_ID,
                        HyperliquidFixture.EXCHANGE_ORDER_ID,
                        OrderState.FILLED,
                        Instant.ofEpochMilli(1_780_000_000_200L)
                ));
    }
}
