package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private TradeDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        dataSource = new TradeDataSource(
                BinanceSpotFixture.BTC_ETH_SOL_REGISTRY,
                restAssistant,
                eventPublisher,
                eventSubscriber
        );
    }

    @Test
    @DisplayName("onStart 시 TradeEvent.UpdateRequested를 구독한다")
    void onStart_subscribesUpdateRequested() {
        dataSource.onStart();

        assertThat(eventSubscriber.isSubscribed(TradeEvent.UpdateRequested.class)).isTrue();
        assertThat(dataSource.phase()).isEqualTo(Phases.TRADE_DATASOURCE_SETUP);
    }

    @Test
    @DisplayName("onShutdown 시 구독을 해제한다")
    void onShutdown_closesSubscription() {
        dataSource.onStart();

        dataSource.onShutdown();

        assertThat(eventSubscriber.count()).isZero();
    }

    @Test
    @DisplayName("주문 체결 내역 조회 성공 시 모든 fill을 담아 Received 이벤트를 발행한다")
    void updateRequest_publishesReceivedEventWithAllFills() {
        TradeEvent.UpdateRequested request = new TradeEvent.UpdateRequested(
                BinanceSpotFixture.CLIENT_ORDER_ID,
                BinanceSpotFixture.EXCHANGE_ORDER_ID,
                BinanceSpotFixture.TRADING_PAIR
        );

        runWith(BinanceSpotFixture.userTradesSuccess(BinanceSpotFixture.EXCHANGE_SYMBOL, BinanceSpotFixture.EXCHANGE_ORDER_ID),
                () -> dataSource.onEvent(request));

        TradeEvent.Received event = eventPublisher.only(TradeEvent.Received.class);
        assertThat(event.clientOrderId()).isEqualTo(BinanceSpotFixture.CLIENT_ORDER_ID);
        assertThat(event.exchangeOrderId()).isEqualTo(BinanceSpotFixture.EXCHANGE_ORDER_ID);
        assertThat(event.tradingPair()).isEqualTo(BinanceSpotFixture.TRADING_PAIR);
        assertThat(event.fills()).containsExactlyElementsOf(List.of(
                new TradeEvent.Fill(
                        "1001",
                        Instant.ofEpochMilli(1_780_000_002_000L),
                        new BigDecimal("50000.00"),
                        new BigDecimal("0.01"),
                        new BigDecimal("500.00"),
                        new TokenAmount("BTC", new BigDecimal("0.00001")),
                        true
                ),
                new TradeEvent.Fill(
                        "1002",
                        Instant.ofEpochMilli(1_780_000_003_000L),
                        new BigDecimal("50100.00"),
                        new BigDecimal("0.02"),
                        new BigDecimal("1002.00"),
                        new TokenAmount("USDT", new BigDecimal("1.002")),
                        false
                )
        ));
    }

    @Test
    @DisplayName("exchangeOrderId가 없으면 trade 조회 없이 예외를 던진다")
    void updateRequest_withoutExchangeOrderId_throws() {
        TradeEvent.UpdateRequested request = new TradeEvent.UpdateRequested(
                BinanceSpotFixture.CLIENT_ORDER_ID,
                null,
                BinanceSpotFixture.TRADING_PAIR
        );

        assertThatThrownBy(() -> dataSource.onEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangeOrderId");
        assertThat(eventPublisher.totalCount()).isZero();
    }

    @Test
    @DisplayName("exchangeOrderId가 UNKNOWN이면 trade 조회 없이 예외를 던진다")
    void updateRequest_withUnknownExchangeOrderId_throws() {
        TradeEvent.UpdateRequested request = new TradeEvent.UpdateRequested(
                BinanceSpotFixture.CLIENT_ORDER_ID,
                "UNKNOWN",
                BinanceSpotFixture.TRADING_PAIR
        );

        assertThatThrownBy(() -> dataSource.onEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangeOrderId");
        assertThat(eventPublisher.totalCount()).isZero();
    }
}
