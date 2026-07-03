package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.derivative.AbstractWsFundingInfoDataSourceTest;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WsFundingInfoDataSourceTest extends AbstractWsFundingInfoDataSourceTest<WsFundingInfoDataSource> {
    private static final ObjectMapper OM = new ObjectMapper();
    @Override
    protected String markPriceStreamUri() {
        return ApiSpec.WSS_MARKET_URL;
    }

    @Override
    protected WsResponse fundingInfoMessage() {
        return BinanceDerivativeFixture.wsMarkPriceMessage("BTCUSDT");
    }

    @Override
    protected FundingInfoEvent.Received expectedReceivedEvent() {
        return new FundingInfoEvent.Received(
                "BTC-USDT",
                BinanceDerivativeFixture.MARK_PRICE_EVENT_TIME,
                BinanceDerivativeFixture.MARK_PRICE_VALUE,
                BinanceDerivativeFixture.MARK_PRICE_FUNDING_RATE,
                BinanceDerivativeFixture.MARK_PRICE_NEXT_FUNDING_TIME,
                null
        );
    }

    @Override
    protected Optional<WsResponse> ackMessage() {
        return Optional.of(BinanceDerivativeFixture.ackResponse(1));
    }

    @Override
    protected WsResponse errorMessage() {
        return BinanceDerivativeFixture.wsErrorResponse(2, "Invalid request");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertAllPairsSubscribed(List<WsRequest> requests) {
        assertThat(requests).hasSize(1);

        WsRequest request = requests.getFirst();
        Map<String, Object> payload = (Map<String, Object>) request.payload();

        assertThat(payload).containsEntry("method", "SUBSCRIBE");
        List<String> params = (List<String>) payload.get("params");

        assertThat(params).containsExactlyInAnyOrder(
                "btcusdt@markPrice",
                "ethusdt@markPrice",
                "solusdt@markPrice"
        );
    }

    @Override
    protected WsFundingInfoDataSource createWsFundingInfoDataSource(MockWsAssistant wsAssistant, TestEventPublisher eventPublisher) {
        return new WsFundingInfoDataSource(
                wsAssistant,
                OM,
                new TestTaskScheduler(),
                event -> { },
                eventPublisher,
                BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY,
                ApiSpec.WSS_MARKET_URL
        );
    }
}
