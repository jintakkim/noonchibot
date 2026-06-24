package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TradeDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        dataSource = new TradeDataSource(
                BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY,
                restAssistant,
                eventPublisher
        );
    }

    @Test
    @DisplayName("주문 체결 내역 조회 성공 시 Received 이벤트에 모든 fill을 담아 발행한다")
    void updateRequest_publishesReceivedEventWithAllFills() {
        TradeEvent.UpdateRequest request = new TradeEvent.UpdateRequest(
                BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID,
                BinanceDerivativeFixture.TRADE_EXCHANGE_ORDER_ID,
                BinanceDerivativeFixture.TRADE_TRADING_PAIR
        );

        runWith(
                BinanceDerivativeFixture.userTradesSuccess(
                        BinanceDerivativeFixture.TRADE_EXCHANGE_SYMBOL,
                        BinanceDerivativeFixture.TRADE_EXCHANGE_ORDER_ID
                ),
                () -> dataSource.onEvent(request)
        );

        TradeEvent.Received event = eventPublisher.only(TradeEvent.Received.class);
        assertThat(event.clientOrderId()).isEqualTo(BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID);
        assertThat(event.exchangeOrderId()).isEqualTo(BinanceDerivativeFixture.TRADE_EXCHANGE_ORDER_ID);
        assertThat(event.tradingPair()).isEqualTo(BinanceDerivativeFixture.TRADE_TRADING_PAIR);
        assertThat(event.fills()).containsExactlyElementsOf(expectedFills());
    }

    @Test
    @DisplayName("exchangeOrderId가 없으면 trade 조회 요청 없이 예외를 던진다")
    void updateRequest_withoutExchangeOrderId_throws() {
        TradeEvent.UpdateRequest request = new TradeEvent.UpdateRequest(
                BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID,
                null,
                BinanceDerivativeFixture.TRADE_TRADING_PAIR
        );

        assertThatThrownBy(() -> dataSource.onEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangeOrderId");

        assertThat(eventPublisher.totalCount()).isZero();
    }

    @Test
    @DisplayName("exchangeOrderId가 UNKNOWN이면 trade 조회 요청 없이 예외를 던진다")
    void updateRequest_withUnknownExchangeOrderId_throws() {
        TradeEvent.UpdateRequest request = new TradeEvent.UpdateRequest(
                BinanceDerivativeFixture.TRADE_CLIENT_ORDER_ID,
                "UNKNOWN",
                BinanceDerivativeFixture.TRADE_TRADING_PAIR
        );

        assertThatThrownBy(() -> dataSource.onEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangeOrderId");

        assertThat(eventPublisher.totalCount()).isZero();
    }

    private List<TradeEvent.Fill> expectedFills() {
        return List.of(
                new TradeEvent.Fill(
                        BinanceDerivativeFixture.USER_TRADE_ID_1,
                        BinanceDerivativeFixture.USER_TRADE_TIME_1,
                        BinanceDerivativeFixture.USER_TRADE_PRICE_1,
                        BinanceDerivativeFixture.USER_TRADE_BASE_AMOUNT_1,
                        BinanceDerivativeFixture.USER_TRADE_QUOTE_AMOUNT_1,
                        new TokenAmount(
                                BinanceDerivativeFixture.USER_TRADE_FEE_ASSET,
                                BinanceDerivativeFixture.USER_TRADE_FEE_1
                        ),
                        false
                ),
                new TradeEvent.Fill(
                        BinanceDerivativeFixture.USER_TRADE_ID_2,
                        BinanceDerivativeFixture.USER_TRADE_TIME_2,
                        BinanceDerivativeFixture.USER_TRADE_PRICE_2,
                        BinanceDerivativeFixture.USER_TRADE_BASE_AMOUNT_2,
                        BinanceDerivativeFixture.USER_TRADE_QUOTE_AMOUNT_2,
                        new TokenAmount(
                                BinanceDerivativeFixture.USER_TRADE_FEE_ASSET,
                                BinanceDerivativeFixture.USER_TRADE_FEE_2
                        ),
                        true
                )
        );
    }
}
