package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public abstract class AbstractOrderBookDataSourceTest {
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    private BlockingQueue<WsResponse> queue;

    protected abstract AbstractOrderBookDataSource createDataSource(
            WsAssistant wsAssistant,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler
    );
    protected abstract WsResponse createAckResponse();
    protected abstract JsonNode createErrorNode(String errorMsg);
    protected abstract JsonNode createDiffMessageNode(String tradingPair);
    protected abstract JsonNode createTradeMessageNode(String tradingPair);

    protected AbstractOrderBookDataSource dataSource;
    private WsAssistant mockWsAssistant;
    protected WsConnection mockWsConnection;
    private IoExecutor ioExecutor;
    protected TaskScheduler mockTaskScheduler; // 하위 클래스에서 verify 할 수 있도록 protected로 열어둠

    @BeforeEach
    void setUp() throws InterruptedException {
        queue = new LinkedBlockingQueue<>();
        mockWsAssistant = Mockito.mock(WsAssistant.class);
        mockWsConnection = Mockito.mock(WsConnection.class);
        ioExecutor = new VirtualThreadIoExecutor();
        mockTaskScheduler = Mockito.mock(TaskScheduler.class);

        when(mockWsConnection.take()).thenAnswer(invocation -> queue.take());
        dataSource = createDataSource(
                mockWsAssistant,
                ioExecutor,
                mockTaskScheduler
        );
        AbstractWebsocketDataSourceTestUtils.setWsConnection(dataSource, mockWsConnection);
    }

    @Test
    @DisplayName("연결(또는 재연결) 시 기존에 구독 중이던 페어들이 있다면 다시 구독 요청을 보낸다")
    void onConnectedResubscribesExistingStreams() {
        dataSource.subscribeOrderBookStream("BTC-USDT");
        dataSource.subscribeOrderBookStream("ETH-USDT");
        // 이전 호출 기록 초기화 (순수하게 onConnected의 동작만 검증하기 위해)
        Mockito.clearInvocations(mockWsConnection);
        dataSource.onConnected();
        // sendSubscribe(Set<String>)에 의해 메시지가 전송되었는지 검증
        verify(mockWsConnection, atLeastOnce()).send(any());
    }

    @Test
    @DisplayName("에러 메시지 수신 시 WebsocketSubscriptionFailedException 예외가 발생한다")
    void processMessageThrowsExceptionOnError() {
        JsonNode errorNode = createErrorNode("invalid symbol");
        queue.add(new WsResponse(errorNode.toString(), WsResponse.MessageType.TEXT));

        // processMessage()는 예외를 던져야 함
        assertThatThrownBy(() -> dataSource.processMessage())
                .isInstanceOf(WebsocketSubscriptionFailedException.class);
    }

    @Test
    @DisplayName("DIFF 메시지 수신 시 파싱되어 해당 페어의 스트림으로 정상 전달(캐스팅)된다")
    void processMessageCastsDiffToStream() throws InterruptedException {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream("BTC-USDT");
        JsonNode diffNode = createDiffMessageNode("BTC-USDT");
        queue.add(new WsResponse(diffNode.toString(), WsResponse.MessageType.TEXT));
        dataSource.processMessage();
        OrderBookMessage message = stream.take();
        assertThat(message).isInstanceOf(OrderBookMessage.DiffMessage.class);
    }

    @Test
    @DisplayName("TRADE 메시지 수신 시 파싱되어 해당 페어의 스트림으로 정상 전달(캐스팅)된다")
    void processMessageCastsTradeToStream() throws InterruptedException {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream("ETH-USDT");
        JsonNode tradeNode = createTradeMessageNode("ETH-USDT");
        queue.add(new WsResponse(tradeNode.toString(), WsResponse.MessageType.TEXT));
        dataSource.processMessage();
        OrderBookMessage message = stream.take();
        assertThat(message).isInstanceOf(OrderBookMessage.TradeMessage.class);
    }
}