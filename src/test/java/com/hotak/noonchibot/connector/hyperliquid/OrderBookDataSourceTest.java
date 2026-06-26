package com.hotak.noonchibot.connector.hyperliquid;

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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookDataSourceTest extends AbstractOrderBookDataSourceTest<OrderBookDataSource> {
    @Override
    protected String wsUri() {
        return DerivativeApiSpec.WS_URL;
    }

    @Override
    protected String testTradingPair() {
        return HyperliquidFixture.TRADING_PAIR;
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
                restAssistant,
                HyperliquidFixture.BTC_ETH_REGISTRY,
                eventPublisher,
                eventSubscriber
        );
    }

    @Override
    protected RestFixture snapshotRestFixture(String tradingPair) {
        return HyperliquidFixture.l2BookSuccess();
    }

    @Override
    protected OrderBookEvent.SnapshotReceived expectedRestSnapshot(String tradingPair) {
        return expectedSnapshot();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertTrackingSubscribeRequests(List<WsRequest> requests) {
        assertThat(requests).hasSize(2);
        List<String> types = requests.stream()
                .map(request -> (Map<String, Object>) request.payload())
                .map(payload -> (Map<String, Object>) payload.get("subscription"))
                .map(subscription -> (String) subscription.get("type"))
                .toList();
        assertThat(types).containsExactlyInAnyOrder("l2Book", "trades");
    }

    @Override
    protected Optional<WsResponse> snapshotWsMessage() {
        return Optional.of(HyperliquidFixture.wsL2Book());
    }

    @Override
    protected OrderBookEvent.SnapshotReceived expectedWsSnapshot() {
        return expectedSnapshot();
    }

    @Override
    protected Optional<WsResponse> tradeWsMessage() {
        return Optional.of(HyperliquidFixture.wsTrades());
    }

    @Override
    protected OrderBookEvent.TradeReceived expectedTrade() {
        return new OrderBookEvent.TradeReceived(
                HyperliquidFixture.TRADING_PAIR,
                123,
                new BigDecimal("50050.0"),
                new BigDecimal("0.01"),
                TradeType.BUY,
                Instant.ofEpochMilli(1_780_000_000_100L)
        );
    }

    @Override
    protected Optional<WsResponse> ackMessage() {
        return Optional.of(HyperliquidFixture.ack());
    }

    @Override
    protected WsResponse errorMessage() {
        return HyperliquidFixture.error();
    }

    private OrderBookEvent.SnapshotReceived expectedSnapshot() {
        return new OrderBookEvent.SnapshotReceived(
                HyperliquidFixture.TRADING_PAIR,
                HyperliquidFixture.BOOK_TIME,
                HyperliquidFixture.BIDS,
                HyperliquidFixture.ASKS,
                HyperliquidFixture.BOOK_INSTANT
        );
    }
}
