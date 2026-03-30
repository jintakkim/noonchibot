package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsConnection;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.AbstractWebsocketDataSourceTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.task.AsyncTaskExecutor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public abstract class AbstractFundingInfoDataSourceTest {
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    private BlockingQueue<WsResponse> queue;
    protected abstract AbstractFundingInfoDataSource createDataSource(WsAssistant wsAssistant, AsyncTaskExecutor taskExecutor);
    protected abstract JsonNode createFundingInfoMessage(String tradingPair, String markPrice, String fundingRate, long nextFundingTime);
    protected abstract WsResponse createAckResponse();
    protected abstract WsResponse createErrorResponse(String errorMsg);

    protected AbstractFundingInfoDataSource dataSource;
    private WsAssistant mockWsAssistant;
    private WsConnection mockWsConnection;
    private AsyncTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() throws InterruptedException {
        queue = new LinkedBlockingQueue<>();
        mockWsAssistant = Mockito.mock(WsAssistant.class);
        mockWsConnection = Mockito.mock(WsConnection.class);
        taskExecutor = Mockito.mock(AsyncTaskExecutor.class);
        when(mockWsConnection.take()).thenAnswer(invocation -> queue.take());
        dataSource = createDataSource(mockWsAssistant, taskExecutor);
        AbstractWebsocketDataSourceTestUtils.setWsConnection(dataSource, mockWsConnection);
    }

    @Test
    @DisplayName("구독 시 FundingInfoMessageStream이 반환된다")
    void subscribeReturnsStream() {
        FundingInfoMessageStream stream = dataSource.subscribe("BTC-USDT");
        assertThat(stream).isNotNull();
        assertThat(stream.tradingPair).isEqualTo("BTC-USDT");
        verify(mockWsConnection, times(1)).send(any(WsRequest.class));
    }

    @Test
    @DisplayName("같은 페어를 여러 번 구독하면 각각 다른 스트림이 반환된다")
    void multipleSubscriptionsReturnDifferentStreams() {
        FundingInfoMessageStream stream1 = dataSource.subscribe("BTC-USDT");
        FundingInfoMessageStream stream2 = dataSource.subscribe("BTC-USDT");
        assertThat(stream1).isNotSameAs(stream2);
        verify(mockWsConnection, times(1)).send(any()); // 구독은 한번만
    }

    @Test
    @DisplayName("구독 해제 후 마지막 스트림이면 구독이 제거된다")
    void unsubscribeRemovesLastStream() {
        FundingInfoMessageStream stream = dataSource.subscribe("BTC-USDT");
        dataSource.unsubscribe(stream);

        // 다시 구독하면 첫 구독처럼 동작 (sendSubscribe 호출됨)
        FundingInfoMessageStream newStream = dataSource.subscribe("BTC-USDT");
        assertThat(newStream).isNotNull();

        verify(mockWsConnection, times(3)).send(any()); // 구독 1번 + 구독 해제 1번 + 구독 1번
    }

    @Test
    @DisplayName("여러 구독 중 하나만 해제하면 나머지는 유지된다")
    void unsubscribeOneKeepsOthers() {
        FundingInfoMessageStream stream1 = dataSource.subscribe("BTC-USDT");
        FundingInfoMessageStream stream2 = dataSource.subscribe("BTC-USDT");

        dataSource.unsubscribe(stream1);
        assertThat(stream2).isNotNull();
        verify(mockWsConnection, times(1)).send(any()); // 구독 1번
    }

    @Test
    @DisplayName("에러 메시지를 정상적으로 감지한다")
    void detectsErrorMessage() {
        JsonNode msg = objectMapper.readTree(createErrorResponse("Invalid request").data());
        assertThat(dataSource.isErrorMessage(msg)).isTrue();
    }

    @Test
    @DisplayName("ACK 메시지를 정상적으로 감지한다")
    void detectsAckMessage() {
        JsonNode msg = objectMapper.readTree(createAckResponse().data());
        assertThat(dataSource.isAckMessage(msg)).isTrue();
    }

    @Test
    @DisplayName("fundingInfo 응답이 FundingInfoMessage로 파싱된다")
    void parsesFundingInfoMessage() {
        JsonNode msg = createFundingInfoMessage("BTC-USDT", "50000.00", "0.0001", 1700000000000L);

        FundingInfoMessage result = dataSource.parseFundingInfoMessage(msg);

        assertThat(result.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(result.nextFundingTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
    }
}