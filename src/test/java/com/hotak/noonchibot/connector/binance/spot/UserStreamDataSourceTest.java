package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.binance.BinanceAuthenticator;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsConnection;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.connector.web.testutils.MockWsConnection;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserStreamDataSourceTest {
    private TestEventPublisher eventPublisher;
    private BinanceAuthenticator authenticator;
    private TestableUserStreamDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        authenticator = mock(BinanceAuthenticator.class);
        when(authenticator.generateWsSubscribeParams()).thenReturn(Map.of(
                "apiKey", "key",
                "timestamp", 1_780_000_000_000L,
                "signature", "sig"
        ));
        dataSource = createDataSource(new MockWsAssistant(), new TestTaskScheduler());
    }

    @Test
    @DisplayName("connection URI는 spot WS API user stream endpoint다")
    void connectionUri_returnsWsApiUrl() {
        assertThat(dataSource.exposeConnectionUri()).isEqualTo(URI.create(ApiSpec.WSS_API_URL));
    }

    @Test
    @DisplayName("연결 직후 userDataStream.subscribe.signature 요청을 보내고 ack를 확인한다")
    void onConnected_subscribesUserStream() {
        MockWsConnection wsConnection = new MockWsConnection(URI.create(ApiSpec.WSS_API_URL), BinanceSpotFixture.userStreamSubscriptionAck());
        dataSource.setConnection(wsConnection);

        dataSource.exposeOnConnected();

        assertThat(wsConnection.sentRequests).hasSize(1);
        assertThat(wsConnection.sentRequests.getFirst().payload())
                .asInstanceOf(MAP)
                .containsEntry("method", "userDataStream.subscribe.signature");
    }

    @Test
    @DisplayName("user stream 인증 실패도 백오프 재연결한다")
    void onStart_authenticationFailureSchedulesReconnect() {
        MockWsAssistant wsAssistant = new MockWsAssistant();
        MockWsConnection connection = wsAssistant.addMockConnection(ApiSpec.WSS_API_URL);
        connection.setResponseFactory(request -> new WsResponse("""
                {
                  "id": "%s",
                  "status": 401,
                  "error": {
                    "code": -2015,
                    "msg": "Invalid API-key, IP, or permissions for action."
                  }
                }
                """.formatted(requestId(request)), WsResponse.MessageType.TEXT));
        TestTaskScheduler taskScheduler = new TestTaskScheduler();
        dataSource = createDataSource(wsAssistant, taskScheduler);

        dataSource.onStart();

        assertThat(connection.isConnected()).isFalse();
        assertThat(taskScheduler.onlyScheduledTask().kind())
                .isEqualTo(TestTaskScheduler.ScheduleKind.ONE_SHOT);
    }

    @Test
    @DisplayName("인증 오류가 아닌 구독 실패는 백오프 재연결한다")
    void onStart_subscriptionFailureSchedulesReconnect() {
        MockWsAssistant wsAssistant = new MockWsAssistant();
        MockWsConnection connection = wsAssistant.addMockConnection(ApiSpec.WSS_API_URL);
        connection.setResponseFactory(request -> new WsResponse("""
                {
                  "id": "%s",
                  "status": 429,
                  "error": {"code": -1003, "msg": "Too many requests"}
                }
                """.formatted(requestId(request)), WsResponse.MessageType.TEXT));
        TestTaskScheduler taskScheduler = new TestTaskScheduler();
        dataSource = createDataSource(wsAssistant, taskScheduler);

        dataSource.onStart();

        assertThat(connection.isConnected()).isFalse();
        assertThat(taskScheduler.onlyScheduledTask().kind())
                .isEqualTo(TestTaskScheduler.ScheduleKind.ONE_SHOT);
    }

    @Test
    @DisplayName("현재 구독 요청과 id가 다른 command 오류 응답은 구독 실패로 판단하지 않는다")
    void processMessage_unmatchedCommandErrorIsIgnored() {
        dataSource.exposeProcessMessage(new WsResponse("""
                {
                  "id": "another-request",
                  "status": 401,
                  "error": {"code": -2015, "msg": "Invalid API-key"}
                }
                """, WsResponse.MessageType.TEXT));

        assertThat(eventPublisher.totalCount()).isZero();
    }

    @Test
    @DisplayName("executionReport 체결 메시지는 TradeReceived와 StatusReceived를 발행한다")
    void executionReportFilled_publishesTradeAndOrderStatus() {
        dataSource.exposeProcessMessage(BinanceSpotFixture.executionReportFilled());

        assertThat(eventPublisher.only(TradeEvent.Received.class))
                .isEqualTo(new TradeEvent.Received(
                        BinanceSpotFixture.CLIENT_ORDER_ID,
                        BinanceSpotFixture.EXCHANGE_ORDER_ID,
                        BinanceSpotFixture.TRADING_PAIR,
                        java.util.List.of(new TradeEvent.Fill(
                                "1001",
                                BinanceSpotFixture.USER_STREAM_EVENT_TIME,
                                new BigDecimal("50000.00"),
                                new BigDecimal("0.01"),
                                new BigDecimal("500.0000"),
                                new TokenAmount("BTC", new BigDecimal("0.00001")),
                                true
                        ))
                ));

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class))
                .isEqualTo(new OrderEvent.StatusReceived(
                        BinanceSpotFixture.TRADING_PAIR,
                        BinanceSpotFixture.CLIENT_ORDER_ID,
                        BinanceSpotFixture.EXCHANGE_ORDER_ID,
                        OrderState.FILLED,
                        BinanceSpotFixture.USER_STREAM_EVENT_TIME
                ));
    }

    @Test
    @DisplayName("outboundAccountPosition 메시지는 BalanceEvent.UpdateReceived를 발행한다")
    void outboundAccountPosition_publishesBalanceUpdate() {
        dataSource.exposeProcessMessage(BinanceSpotFixture.outboundAccountPosition());

        BalanceEvent.UpdateReceived event = eventPublisher.only(BalanceEvent.UpdateReceived.class);
        assertThat(event.timestamp()).isEqualTo(BinanceSpotFixture.USER_STREAM_EVENT_TIME);
        assertThat(event.assets()).containsEntry("BTC", new AssetState(
                new BigDecimal("0.12"),
                new BigDecimal("0.10"),
                BinanceSpotFixture.USER_STREAM_EVENT_TIME
        ));
    }

    @Test
    @DisplayName("phase는 USER_STREAM_DATASOURCE_SETUP이다")
    void phase_returnsUserStreamDataSourceSetup() {
        assertThat(dataSource.phase()).isEqualTo(Phases.USER_STREAM_DATASOURCE_SETUP);
    }

    private TestableUserStreamDataSource createDataSource(WsAssistant wsAssistant, TestTaskScheduler taskScheduler) {
        return new TestableUserStreamDataSource(
                wsAssistant,
                new ObjectMapper(),
                authenticator,
                BinanceSpotFixture.BTC_ETH_SOL_REGISTRY,
                eventPublisher,
                taskScheduler,
                ApiSpec.WSS_API_URL
        );
    }

    private static Object requestId(WsRequest request) {
        return ((Map<?, ?>) request.payload()).get("id");
    }

    private static class TestableUserStreamDataSource extends UserStreamDataSource {
        TestableUserStreamDataSource(
                WsAssistant wsAssistant,
                ObjectMapper objectMapper,
                BinanceAuthenticator authenticator,
                com.hotak.noonchibot.connector.TradingPairSymbolRegistry tradingPairSymbolRegistry,
                TestEventPublisher eventPublisher,
                TestTaskScheduler taskScheduler,
                String websocketApiUrl
        ) {
            super(
                    wsAssistant,
                    objectMapper,
                    authenticator,
                    tradingPairSymbolRegistry,
                    eventPublisher,
                    taskScheduler,
                    event -> { },
                    websocketApiUrl
            );
        }

        URI exposeConnectionUri() {
            return connectionUri();
        }

        void exposeOnConnected() {
            handleConnected();
        }

        void exposeProcessMessage(WsResponse response) {
            processMessage(response);
        }

        void setConnection(WsConnection wsConnection) {
            this.wsConnection = wsConnection;
        }
    }
}
