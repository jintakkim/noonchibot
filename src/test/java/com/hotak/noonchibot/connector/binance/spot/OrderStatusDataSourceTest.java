package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private OrderStatusDataSource dataSource;
    private TestEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        subscriber = new TestEventSubscriber();
        dataSource = new OrderStatusDataSource(
                BinanceSpotFixture.BTC_ETH_SOL_REGISTRY,
                restAssistant,
                eventPublisher,
                subscriber
        );
    }

    @Test
    @DisplayName("시작 시 StatusUpdateRequested 이벤트를 concurrent policy로 구독한다")
    void onStart_subscribesStatusUpdateRequested() {
        dataSource.onStart();

        com.hotak.noonchibot.core.event.EventSubscriberAssert.assertThat(subscriber)
                .hasSubscriptionCount(1)
                .isSubscribedTo(OrderEvent.StatusUpdateRequested.class)
                .subscription(OrderEvent.StatusUpdateRequested.class)
                .usesConcurrentPolicy();
        assertThat(dataSource.phase()).isEqualTo(Phases.ORDER_STATUS_DATASOURCE_SETUP);
    }

    @Test
    @DisplayName("종료 시 구독을 해제한다")
    void onShutdown_closesSubscription() {
        dataSource.onStart();

        dataSource.onShutdown();

        com.hotak.noonchibot.core.event.EventSubscriberAssert.assertThat(subscriber)
                .hasNoSubscriptions();
    }

    @Test
    @DisplayName("주문 상태 조회 성공 시 StatusReceived 이벤트를 발행한다")
    void statusUpdateRequest_publishesStatusReceived() {
        OrderEvent.StatusUpdateRequested request = new OrderEvent.StatusUpdateRequested(
                BinanceSpotFixture.TRADING_PAIR,
                BinanceSpotFixture.CLIENT_ORDER_ID
        );

        runWith(BinanceSpotFixture.orderStatusSuccess(BinanceSpotFixture.EXCHANGE_SYMBOL, BinanceSpotFixture.CLIENT_ORDER_ID),
                () -> dataSource.onEvent(request));

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class))
                .isEqualTo(new OrderEvent.StatusReceived(
                        BinanceSpotFixture.TRADING_PAIR,
                        BinanceSpotFixture.CLIENT_ORDER_ID,
                        BinanceSpotFixture.EXCHANGE_ORDER_ID,
                        OrderState.FILLED,
                        BinanceSpotFixture.USER_STREAM_EVENT_TIME
                ));
    }
}
