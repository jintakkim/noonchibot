package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.web.TimeSynchronizer;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderBookDataSourceTest extends AbstractOrderBookDataSourceTest<OrderBookDataSource> {
    @Override
    protected String wsUri() {
        return ApiSpec.WSS_URL;
    }

    @Override
    protected OrderBookDataSource createOrderBookDataSource(
            MockWsAssistant wsAssistant,
            MockRestAssistant restAssistant,
            TestTaskScheduler taskScheduler,
            TestEventPublisher eventPublisher,
            TestEventSubscriber eventSubscriber
    ) {
        TimeSynchronizer timeSynchronizer = mock(TimeSynchronizer.class);
        when(timeSynchronizer.serverTime()).thenReturn(BinanceSpotFixture.SERVER_TIME);
        return new OrderBookDataSource(
                wsAssistant,
                new ObjectMapper(),
                new VirtualThreadIoExecutor(),
                taskScheduler,
                BinanceSpotFixture.BTC_ETH_SOL_REGISTRY,
                restAssistant,
                timeSynchronizer,
                eventPublisher,
                eventSubscriber
        );
    }

    @Override
    protected RestFixture snapshotRestFixture(String tradingPair) {
        return BinanceSpotFixture.depthSnapshotSuccess(BinanceSpotFixture.EXCHANGE_SYMBOL);
    }

    @Override
    protected OrderBookEvent.SnapshotReceived expectedRestSnapshot(String tradingPair) {
        return new OrderBookEvent.SnapshotReceived(
                tradingPair,
                BinanceSpotFixture.SNAPSHOT_UPDATE_ID,
                BinanceSpotFixture.SNAPSHOT_BIDS,
                BinanceSpotFixture.SNAPSHOT_ASKS,
                BinanceSpotFixture.SERVER_INSTANT
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertTrackingSubscribeRequests(List<WsRequest> requests) {
        assertThat(requests).hasSize(2);
        List<String> params = requests.stream()
                .flatMap(request -> ((List<String>) ((Map<String, Object>) request.payload()).get("params")).stream())
                .toList();
        assertThat(params).containsExactlyInAnyOrder("btcusdt@trade", "btcusdt@depth@100ms");
    }

    @Override
    protected Optional<WsResponse> diffWsMessage() {
        return Optional.of(BinanceSpotFixture.wsDepthUpdate(BinanceSpotFixture.EXCHANGE_SYMBOL));
    }

    @Override
    protected OrderBookEvent.DiffReceived expectedDiff() {
        return new OrderBookEvent.DiffReceived(
                BinanceSpotFixture.TRADING_PAIR,
                BinanceSpotFixture.DIFF_UPDATE_ID,
                BinanceSpotFixture.DIFF_BIDS,
                BinanceSpotFixture.DIFF_ASKS,
                BinanceSpotFixture.DIFF_EVENT_TIME
        );
    }

    @Override
    protected OrderBookEvent.SnapshotReceived expectedWsSnapshot() {
        throw new UnsupportedOperationException("binance spot does not use ws snapshot");
    }

    @Override
    protected Optional<WsResponse> tradeWsMessage() {
        return Optional.of(BinanceSpotFixture.wsTrade(BinanceSpotFixture.EXCHANGE_SYMBOL));
    }

    @Override
    protected OrderBookEvent.TradeReceived expectedTrade() {
        return new OrderBookEvent.TradeReceived(
                BinanceSpotFixture.TRADING_PAIR,
                BinanceSpotFixture.TRADE_ID,
                BinanceSpotFixture.TRADE_PRICE,
                BinanceSpotFixture.TRADE_QTY,
                TradeType.BUY,
                BinanceSpotFixture.TRADE_TIME
        );
    }

    @Override
    protected Optional<WsResponse> ackMessage() {
        return Optional.of(BinanceSpotFixture.ackResponse(1));
    }

    @Override
    protected WsResponse errorMessage() {
        return BinanceSpotFixture.wsErrorResponse(-1, "Invalid request");
    }
}
