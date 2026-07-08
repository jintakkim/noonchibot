package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.connector.web.testutils.MockRestAssistant;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.connector.web.testutils.MockWsConnection;
import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.connector.web.testutils.RestFixture;
import com.hotak.noonchibot.core.AbstractWebsocketDataSourceTestUtils;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.event.EventMetadata;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.orderbook.OrderBookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractOrderBookDataSourceTest<T extends AbstractOrderBookDataSource> extends RestClientTest {
    protected static final ObjectMapper OM = new ObjectMapper();
    protected static final ObjectMapper objectMapper = OM;

    protected T dataSource;
    protected MockWsConnection wsConnection;
    protected TestTaskScheduler taskScheduler;
    protected TestEventPublisher eventPublisher;
    protected TestEventSubscriber eventSubscriber;
    protected MockWsConnection mockWsConnection;
    protected TestTaskScheduler mockTaskScheduler;

    protected AbstractOrderBookDataSourceTest() {
    }

    protected AbstractOrderBookDataSourceTest(String quoteAsset) {
    }

    @BeforeEach
    void setUpOrderBookDataSource() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        taskScheduler = new TestTaskScheduler();
        wsConnection = new MockWsConnection(URI.create(wsUri()));
        mockWsConnection = wsConnection;
        mockTaskScheduler = taskScheduler;

        dataSource = createOrderBookDataSource(
                new MockWsAssistant(),
                restAssistant,
                taskScheduler,
                eventPublisher,
                eventSubscriber
        );
        AbstractWebsocketDataSourceTestUtils.setWsConnection(dataSource, wsConnection);
    }

    protected abstract T createOrderBookDataSource(
            MockWsAssistant wsAssistant,
            MockRestAssistant restAssistant,
            TestTaskScheduler taskScheduler,
            TestEventPublisher eventPublisher,
            TestEventSubscriber eventSubscriber
    );

    protected String wsUri() {
        return "ws://test";
    }

    protected String testTradingPair() {
        return "BTC-USDT";
    }

    protected abstract RestFixture snapshotRestFixture(String tradingPair);

    protected abstract OrderBookEvent.SnapshotReceived expectedRestSnapshot(String tradingPair);

    protected abstract void assertTrackingSubscribeRequests(List<WsRequest> requests);

    protected Optional<WsResponse> diffWsMessage() {
        return Optional.empty();
    }

    protected OrderBookEvent.DiffReceived expectedDiff() {
        throw new UnsupportedOperationException("expectedDiffEvent is not implemented");
    }

    protected Optional<WsResponse> snapshotWsMessage() {
        return Optional.empty();
    }

    protected abstract OrderBookEvent.SnapshotReceived expectedWsSnapshot();

    protected Optional<WsResponse> tradeWsMessage() {
        return Optional.empty();
    }

    protected abstract OrderBookEvent.TradeReceived expectedTrade();

    protected Optional<WsResponse> ackMessage() {
        return Optional.empty();
    }

    protected abstract WsResponse errorMessage();

    @Test
    @DisplayName("REST 스냅샷을 파싱한다")
    void getOrderBookSnapshotParsesCorrectly() {
        String tradingPair = testTradingPair();
        runWith(snapshotRestFixture(tradingPair), () -> {
            OrderBookEvent.SnapshotReceived snapshot = dataSource.fetchOrderBookSnapshot(tradingPair);
            assertThat(snapshot).isEqualTo(expectedRestSnapshot(tradingPair));
        });
    }

    @Test
    @DisplayName("tracking 요청 수신 시 WS를 구독하고 REST 스냅샷 이벤트를 발행한다")
    void trackingRequestSubscribesWsAndPublishesRestSnapshot() {
        String tradingPair = testTradingPair();
        runWith(snapshotRestFixture(tradingPair), () -> {
            dataSource.trackingOrderBook(new OrderBookEvent.TrackingRequested(tradingPair));
            assertTrackingSubscribeRequests(wsConnection.sentRequests);
            assertThat(eventPublisher.only(OrderBookEvent.SnapshotReceived.class))
                    .isEqualTo(expectedRestSnapshot(tradingPair));
        });
    }

    @Test
    @DisplayName("중복 tracking 요청 수신 시 추가 구독과 스냅샷 요청을 하지 않는다")
    void duplicateTrackingRequestIsIgnored() {
        String tradingPair = testTradingPair();
        runWith(snapshotRestFixture(tradingPair), () -> {
            dataSource.trackingOrderBook(new OrderBookEvent.TrackingRequested(tradingPair));
            dataSource.trackingOrderBook(new OrderBookEvent.TrackingRequested(tradingPair));

            assertTrackingSubscribeRequests(wsConnection.sentRequests);
            assertThat(eventPublisher.countEventsOfType(OrderBookEvent.SnapshotReceived.class)).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("diff 메시지 수신 시 DiffReceived 이벤트를 발행한다")
    void diffMessagePublishesDiffEvent() {
        assumeTrue(diffWsMessage().isPresent(), "이 거래소는 diff 메시지를 사용하지 않음");

        dataSource.processMessage(diffWsMessage().get());

        assertThat(eventPublisher.getFirstEventOfType(OrderBookEvent.DiffReceived.class))
                .hasValue(expectedDiff());
    }

    @Test
    @DisplayName("snapshot 메시지 수신 시 SnapshotReceived 이벤트를 발행한다")
    void snapshotMessagePublishesSnapshotEvent() {
        assumeTrue(snapshotWsMessage().isPresent(), "이 거래소는 snapshot 메시지를 사용하지 않음");

        dataSource.processMessage(snapshotWsMessage().get());

        assertThat(eventPublisher.getFirstEventOfType(OrderBookEvent.SnapshotReceived.class))
                .hasValue(expectedWsSnapshot());
    }

    @Test
    @DisplayName("trade 메시지 수신 시 TradeReceived 이벤트를 발행한다")
    void tradeMessagePublishesTradeEvent() {
        assumeTrue(tradeWsMessage().isPresent(), "이 거래소는 trade 메시지를 사용하지 않음");

        dataSource.processMessage(tradeWsMessage().get());

        assertThat(eventPublisher.getFirstEventOfType(OrderBookEvent.TradeReceived.class))
                .hasValue(expectedTrade());
    }

    @Test
    @DisplayName("ack 메시지는 무시되고 이벤트를 발행하지 않는다")
    void ackMessageDoesNotPublishEvent() {
        assumeTrue(ackMessage().isPresent(), "이 거래소는 ack 메시지를 사용하지 않음");

        dataSource.processMessage(ackMessage().get());
        assertThat(eventPublisher.totalCount()).isZero();
    }

    @Test
    @DisplayName("error 메시지 수신 시 예외를 던진다")
    void errorMessageThrows() {
        assertThatThrownBy(() -> dataSource.processMessage(errorMessage()))
                .isInstanceOf(WebSocketErrorMessageReceivedException.class);
    }
}
