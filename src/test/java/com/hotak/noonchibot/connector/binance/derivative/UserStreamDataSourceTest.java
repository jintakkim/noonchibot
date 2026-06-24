package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockRestAssistant;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import com.hotak.noonchibot.core.config.Phases;
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
import java.time.Duration;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class UserStreamDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TestTaskScheduler taskScheduler;
    private TestableUserStreamDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        taskScheduler = new TestTaskScheduler();
        dataSource = createDataSource(restAssistant, new MockWsAssistant(), new VirtualThreadIoExecutor());
    }

    @Test
    @DisplayName("listenKey를 발급받아 user stream websocket URI를 만든다")
    void connectionUri_fetchesListenKeyAndBuildsPrivateWsUri() {
        runWith(BinanceDerivativeFixture.listenKeyCreateSuccess(), () -> {
            URI uri = dataSource.exposeConnectionUri();

            assertThat(uri.toString())
                    .isEqualTo(ApiSpec.WSS_PRIVATE_URL + "/" + BinanceDerivativeFixture.LISTEN_KEY);
        });
    }

    @Test
    @DisplayName("시작 시 listenKey keep-alive task를 45분 fixed delay로 등록한다")
    void onStart_registersListenKeyKeepAliveTask() {
        IoExecutor ioExecutor = mock(IoExecutor.class);
        Future<?> connectionFuture = mock(Future.class);
        doReturn(connectionFuture).when(ioExecutor).submit(any(Runnable.class));
        dataSource = createDataSource(restAssistant, new MockWsAssistant(), ioExecutor);

        dataSource.onStart();

        assertThat(taskScheduler.onlyScheduledTask().kind())
                .isEqualTo(TestTaskScheduler.ScheduleKind.FIXED_DELAY);
        assertThat(taskScheduler.onlyScheduledTask().delay())
                .isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    @DisplayName("keep-alive task 실행 시 listenKey 갱신 REST 요청을 보낸다")
    void keepAliveTask_renewsListenKey() {
        IoExecutor ioExecutor = mock(IoExecutor.class);
        Future<?> connectionFuture = mock(Future.class);
        doReturn(connectionFuture).when(ioExecutor).submit(any(Runnable.class));
        dataSource = createDataSource(restAssistant, new MockWsAssistant(), ioExecutor);

        dataSource.onStart();

        runWith(BinanceDerivativeFixture.listenKeyKeepAliveSuccess(), () ->
                taskScheduler.onlyScheduledTask().task().run()
        );
    }

    @Test
    @DisplayName("ORDER_TRADE_UPDATE 체결 메시지는 TradeReceived와 StatusReceived를 발행한다")
    void orderTradeUpdateFilled_publishesTradeAndOrderStatus() {
        dataSource.exposeProcessMessage(BinanceDerivativeFixture.orderTradeUpdateFilled());

        TradeEvent.Received trade = eventPublisher.only(TradeEvent.Received.class);
        assertThat(trade.clientOrderId()).isEqualTo(BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID);
        assertThat(trade.exchangeOrderId()).isEqualTo(BinanceDerivativeFixture.TRADE_EXCHANGE_ORDER_ID);
        assertThat(trade.tradingPair()).isEqualTo(BinanceDerivativeFixture.TRADE_TRADING_PAIR);
        assertThat(trade.fills()).containsExactly(new TradeEvent.Fill(
                BinanceDerivativeFixture.USER_TRADE_ID_1,
                BinanceDerivativeFixture.USER_TRADE_TIME_1,
                BinanceDerivativeFixture.USER_TRADE_PRICE_1,
                BinanceDerivativeFixture.USER_TRADE_BASE_AMOUNT_1,
                BinanceDerivativeFixture.USER_TRADE_PRICE_1.multiply(BinanceDerivativeFixture.USER_TRADE_BASE_AMOUNT_1),
                new TokenAmount(
                        BinanceDerivativeFixture.USER_TRADE_FEE_ASSET,
                        BinanceDerivativeFixture.USER_TRADE_FEE_1
                ),
                true
        ));

        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class))
                .isEqualTo(new OrderEvent.StatusReceived(
                        BinanceDerivativeFixture.TRADE_TRADING_PAIR,
                        BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID,
                        BinanceDerivativeFixture.TRADE_EXCHANGE_ORDER_ID,
                        OrderState.FILLED,
                        BinanceDerivativeFixture.USER_STREAM_EVENT_TIME
                ));
    }

    @Test
    @DisplayName("ORDER_TRADE_UPDATE 미체결 메시지는 StatusReceived만 발행한다")
    void orderTradeUpdateWithoutFill_publishesOnlyOrderStatus() {
        dataSource.exposeProcessMessage(BinanceDerivativeFixture.orderTradeUpdateWithoutFill());

        assertThat(eventPublisher.countEventsOfType(TradeEvent.Received.class)).isZero();
        assertThat(eventPublisher.only(OrderEvent.StatusReceived.class))
                .isEqualTo(new OrderEvent.StatusReceived(
                        BinanceDerivativeFixture.TRADE_TRADING_PAIR,
                        BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID,
                        BinanceDerivativeFixture.TRADE_EXCHANGE_ORDER_ID,
                        OrderState.OPEN,
                        BinanceDerivativeFixture.USER_STREAM_EVENT_TIME
                ));
    }

    @Test
    @DisplayName("ACCOUNT_UPDATE 포지션 메시지는 PositionEvent.UpdateReceived를 발행한다")
    void accountUpdate_publishesPositionUpdate() {
        dataSource.exposeProcessMessage(BinanceDerivativeFixture.accountUpdateWithPosition());

        assertThat(eventPublisher.only(PositionEvent.UpdateReceived.class))
                .isEqualTo(new PositionEvent.UpdateReceived(
                        BinanceDerivativeFixture.TRADE_TRADING_PAIR,
                        PositionSide.SHORT,
                        BinanceDerivativeFixture.POSITION_AMOUNT,
                        BinanceDerivativeFixture.POSITION_ENTRY_PRICE,
                        BinanceDerivativeFixture.POSITION_UNREALIZED_PNL,
                        BinanceDerivativeFixture.ACCOUNT_UPDATE_TIME
                ));
    }

    @Test
    @DisplayName("알 수 없는 user stream 이벤트는 이벤트를 발행하지 않는다")
    void unknownEvent_doesNotPublishAnyEvent() {
        dataSource.exposeProcessMessage(new WsResponse("""
                {
                    "e": "UNKNOWN_EVENT"
                }
                """, WsResponse.MessageType.TEXT));

        assertThat(eventPublisher.totalCount()).isZero();
    }

    @Test
    @DisplayName("종료 시 listenKey keep-alive task를 취소한다")
    void onShutdown_cancelsListenKeyKeepAliveTask() {
        IoExecutor ioExecutor = mock(IoExecutor.class);
        Future<?> connectionFuture = mock(Future.class);
        doReturn(connectionFuture).when(ioExecutor).submit(any(Runnable.class));
        dataSource = createDataSource(restAssistant, new MockWsAssistant(), ioExecutor);
        dataSource.onStart();

        dataSource.onShutdown();

        assertThat(taskScheduler.onlyScheduledTask().isCancelled()).isTrue();
    }

    @Test
    @DisplayName("phase는 USER_STREAM_DATASOURCE_SETUP이다")
    void phase_returnsUserStreamDataSourceSetup() {
        assertThat(dataSource.phase()).isEqualTo(Phases.USER_STREAM_DATASOURCE_SETUP);
    }

    private TestableUserStreamDataSource createDataSource(
            MockRestAssistant restAssistant,
            WsAssistant wsAssistant,
            IoExecutor ioExecutor
    ) {
        return new TestableUserStreamDataSource(
                restAssistant,
                wsAssistant,
                taskScheduler,
                new ObjectMapper(),
                BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY,
                eventPublisher,
                ioExecutor
        );
    }

    private static class TestableUserStreamDataSource extends UserStreamDataSource {
        TestableUserStreamDataSource(
                RestAssistant restAssistant,
                WsAssistant wsAssistant,
                TestTaskScheduler taskScheduler,
                ObjectMapper objectMapper,
                com.hotak.noonchibot.connector.TradingPairSymbolRegistry tradingPairSymbolRegistry,
                TestEventPublisher eventPublisher,
                IoExecutor ioExecutor
        ) {
            super(restAssistant, wsAssistant, taskScheduler, objectMapper, tradingPairSymbolRegistry, eventPublisher, ioExecutor);
        }

        URI exposeConnectionUri() {
            return connectionUri();
        }

        void exposeProcessMessage(WsResponse response) {
            processMessage(response);
        }
    }
}
