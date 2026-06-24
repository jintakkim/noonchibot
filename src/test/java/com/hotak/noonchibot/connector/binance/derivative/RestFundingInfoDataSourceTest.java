package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestFundingInfoDataSourceTest extends RestClientTest {
    private TestEventPublisher eventPublisher;
    private final TradingPairSymbolRegistry symbolRegistry = BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY;
    private RestFundingInfoDataSource dataSource;

    @BeforeEach
    void setup() {
        eventPublisher = new TestEventPublisher();
        dataSource = new RestFundingInfoDataSource(
                symbolRegistry,
                restAssistant,
                eventPublisher
        );
    }
    @Test
    @DisplayName("펀딩 정보 조회 성공시 Received 이벤트 발행")
    void fundingInfoFetchSuccessPublishesEvent() {
        runWith(
                BinanceDerivativeFixture.premiumIndexSuccess("BTCUSDT"),
                () -> {
                    dataSource.onEvent(new FundingInfoEvent.RestFetchRequested("BTC-USDT"));
                    var occurred = eventPublisher.getFirstEventOfType(FundingInfoEvent.Received.class);
                    assertThat(occurred)
                            .isPresent()
                            .hasValueSatisfying(event -> {
                                assertThat(event.tradingPair()).isEqualTo("BTC-USDT");
                                assertThat(event.eventTime()).isNotNull();
                                assertThat(event.markPrice()).isNotNull();
                                assertThat(event.fundingRate()).isNotNull();
                                assertThat(event.nextFundingTime()).isNotNull();
                            });
                }
        );
    }

    @Test
    @DisplayName("실패시 RestFetchFailed 이벤트 발행")
    void failureOccursFailedEvent() {
        dataSource.onFailure(
                new FundingInfoEvent.RestFetchRequested("BTC-USDT"),
                new IllegalStateException("funding info fetch failed")
        );
        assertThat(eventPublisher.hasEventOfType(FundingInfoEvent.RestFetchFailed.class)).isTrue();
    }
}