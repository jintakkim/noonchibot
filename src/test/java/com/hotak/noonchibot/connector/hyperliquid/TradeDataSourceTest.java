package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
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

class TradeDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private TradeDataSource dataSource;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        dataSource = new TradeDataSource(
                HyperliquidFixture.BTC_ETH_REGISTRY,
                restAssistant,
                eventPublisher,
                new TestEventSubscriber(),
                HyperliquidFixture.USER
        );
    }

    @Test
    @DisplayName("userFills 조회 결과 중 주문 oid와 일치하는 fill만 Received에 담는다")
    void onEvent_publishesMatchingFills() {
        TradeEvent.UpdateRequested request = new TradeEvent.UpdateRequested(
                HyperliquidFixture.CLIENT_ORDER_ID,
                HyperliquidFixture.EXCHANGE_ORDER_ID,
                HyperliquidFixture.TRADING_PAIR
        );

        runWith(HyperliquidFixture.userFillsSuccess(), () -> dataSource.onEvent(request));

        assertThat(eventPublisher.only(TradeEvent.Received.class))
                .isEqualTo(new TradeEvent.Received(
                        HyperliquidFixture.CLIENT_ORDER_ID,
                        HyperliquidFixture.EXCHANGE_ORDER_ID,
                        HyperliquidFixture.TRADING_PAIR,
                        List.of(new TradeEvent.Fill(
                                "456",
                                Instant.ofEpochMilli(1_780_000_000_300L),
                                new BigDecimal("50000.0"),
                                new BigDecimal("0.01"),
                                new BigDecimal("500.000"),
                                new TokenAmount("USDC", new BigDecimal("0.01")),
                                true
                        ))
                ));
    }
}
