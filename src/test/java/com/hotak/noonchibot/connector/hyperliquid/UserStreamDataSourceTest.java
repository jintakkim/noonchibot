package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.WsConnection;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.connector.web.testutils.MockWsConnection;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.PositionEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.trade.TokenAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class UserStreamDataSourceTest {
    private TestEventPublisher eventPublisher;
    private TestableUserStreamDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        dataSource = new TestableUserStreamDataSource(new MockWsAssistant(), new ObjectMapper(), new VirtualThreadIoExecutor());
    }

    @Test
    @DisplayName("연결 시 orderUpdates/userFills/clearinghouseState를 구독한다")
    void onConnected_subscribesUserChannels() {
        MockWsConnection wsConnection = new MockWsConnection(URI.create(DerivativeApiSpec.WS_URL));
        dataSource.setConnection(wsConnection);

        dataSource.exposeOnConnected();

        assertThat(wsConnection.sentRequests).hasSize(3);
    }

    @Test
    @DisplayName("orderUpdates 메시지는 StatusReceived를 발행한다")
    void orderUpdates_publishStatusReceived() {
        dataSource.exposeProcessMessage(HyperliquidFixture.orderUpdates());

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class))
                .isEqualTo(new OrderEvent.StatusReceived(
                        HyperliquidFixture.TRADING_PAIR,
                        HyperliquidFixture.CLIENT_ORDER_ID,
                        HyperliquidFixture.EXCHANGE_ORDER_ID,
                        OrderState.FILLED,
                        InstantFixtures.ORDER_STATUS_TIME
                ));
    }

    @Test
    @DisplayName("userFills 스트리밍 메시지는 TradeEvent.Received를 발행한다")
    void userFills_publishTradeReceived() {
        dataSource.exposeProcessMessage(HyperliquidFixture.userFills());

        assertThat(eventPublisher.only(TradeEvent.Received.class))
                .isEqualTo(new TradeEvent.Received(
                        null,
                        HyperliquidFixture.EXCHANGE_ORDER_ID,
                        HyperliquidFixture.TRADING_PAIR,
                        java.util.List.of(new TradeEvent.Fill(
                                "456",
                                InstantFixtures.TRADE_TIME,
                                new BigDecimal("50000.0"),
                                new BigDecimal("0.01"),
                                new BigDecimal("500.000"),
                                new TokenAmount("USDC", new BigDecimal("0.01")),
                                false
                        ))
                ));
    }

    @Test
    @DisplayName("clearinghouseState 메시지는 포지션 업데이트를 발행한다")
    void clearinghouseState_publishPositionUpdate() {
        dataSource.exposeProcessMessage(HyperliquidFixture.clearinghouseState());

        PositionEvent.UpdateReceived event = eventPublisher.only(PositionEvent.UpdateReceived.class);
        assertThat(event.tradingPair()).isEqualTo(HyperliquidFixture.TRADING_PAIR);
        assertThat(event.positionSide()).isEqualTo(PositionSide.SHORT);
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("-0.02"));
        assertThat(event.entryPrice()).isEqualByComparingTo(new BigDecimal("50000.0"));
        assertThat(event.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("12.3"));
    }

    private class TestableUserStreamDataSource extends UserStreamDataSource {
        TestableUserStreamDataSource(MockWsAssistant wsAssistant, ObjectMapper objectMapper, IoExecutor ioExecutor) {
            super(wsAssistant, objectMapper, ioExecutor, HyperliquidFixture.USER, HyperliquidFixture.BTC_ETH_REGISTRY, eventPublisher);
        }

        void setConnection(WsConnection wsConnection) {
            this.wsConnection = wsConnection;
        }

        void exposeOnConnected() {
            onConnected();
        }

        void exposeProcessMessage(WsResponse response) {
            processMessage(response);
        }
    }

    private static final class InstantFixtures {
        static final java.time.Instant ORDER_STATUS_TIME = java.time.Instant.ofEpochMilli(1_780_000_000_200L);
        static final java.time.Instant TRADE_TIME = java.time.Instant.ofEpochMilli(1_780_000_000_300L);
    }
}
