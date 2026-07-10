package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockRestAssistant;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.connector.web.testutils.RestFixture;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSourceTest;
import com.hotak.noonchibot.core.trade.TradeType;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookDataSourceTest extends AbstractOrderBookDataSourceTest<OrderBookDataSource> {
    @Override
    protected String wsUri() {
        return ApiSpec.WSS_MARKET_URL;
    }

    @Override
    protected String testTradingPair() {
        return "BTC-USDT";
    }

    @Override
    protected OrderBookDataSource createOrderBookDataSource(
            MockWsAssistant wsAssistant,
            MockRestAssistant restAssistant,
            TestTaskScheduler taskScheduler,
            TestEventPublisher eventPublisher,
            TestEventSubscriber eventSubscriber
    ) {
        return new OrderBookDataSource(
                wsAssistant,
                new ObjectMapper(),
                new VirtualThreadIoExecutor(),
                taskScheduler,
                event -> { },
                BinanceDerivativeFixture.BTC_ETH_SOL_REGISTRY,
                restAssistant,
                ApiSpec.WSS_PUBLIC_URL,
                eventPublisher,
                eventSubscriber
        );
    }

    @Override
    protected RestFixture snapshotRestFixture(String tradingPair) {
        return BinanceDerivativeFixture.depthSnapshotSuccess("BTCUSDT");
    }

    @Override
    protected OrderBookEvent.SnapshotReceived expectedRestSnapshot(String tradingPair) {
        return new OrderBookEvent.SnapshotReceived(
                tradingPair,
                BinanceDerivativeFixture.SNAPSHOT_UPDATE_ID,
                BinanceDerivativeFixture.SNAPSHOT_BIDS,
                BinanceDerivativeFixture.SNAPSHOT_ASKS,
                BinanceDerivativeFixture.SNAPSHOT_EVENT_TIME
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertTrackingSubscribeRequests(List<WsRequest> requests) {
        assertThat(requests).hasSize(2);

        List<String> params = requests.stream()
                .flatMap(request -> ((List<String>) ((Map<String, Object>) request.payload()).get("params")).stream())
                .toList();

        assertThat(params).containsExactlyInAnyOrder("btcusdt@aggTrade", "btcusdt@depth");
    }

    @Override
    protected Optional<WsResponse> diffWsMessage() {
        return Optional.of(BinanceDerivativeFixture.wsDepthUpdate("BTCUSDT"));
    }

    @Override
    protected OrderBookEvent.DiffReceived expectedDiff() {
        return new OrderBookEvent.DiffReceived(
                "BTC-USDT",
                BinanceDerivativeFixture.DIFF_LAST_UPDATE_ID,
                BinanceDerivativeFixture.DIFF_BIDS,
                BinanceDerivativeFixture.DIFF_ASKS,
                BinanceDerivativeFixture.DIFF_EVENT_TIME
        );
    }

    @Override
    protected OrderBookEvent.SnapshotReceived expectedWsSnapshot() {
        throw new UnsupportedOperationException("binance derivative does not use ws snapshot");
    }

    @Override
    protected Optional<WsResponse> tradeWsMessage() {
        return Optional.of(BinanceDerivativeFixture.wsAggTrade("BTCUSDT"));
    }

    @Override
    protected OrderBookEvent.TradeReceived expectedTrade() {
        return new OrderBookEvent.TradeReceived(
                "BTC-USDT",
                BinanceDerivativeFixture.TRADE_ID,
                BinanceDerivativeFixture.TRADE_PRICE,
                BinanceDerivativeFixture.TRADE_QTY,
                TradeType.BUY,
                BinanceDerivativeFixture.TRADE_TIME
        );
    }

    @Override
    protected Optional<WsResponse> ackMessage() {
        return Optional.of(BinanceDerivativeFixture.ackResponse(1));
    }

    @Override
    protected WsResponse errorMessage() {
        return BinanceDerivativeFixture.wsErrorResponse(-1, "Invalid request");
    }
}
