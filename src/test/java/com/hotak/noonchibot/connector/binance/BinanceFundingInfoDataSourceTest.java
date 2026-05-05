package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.AbstractFundingInfoDataSource;
import com.hotak.noonchibot.core.orderbook.AbstractFundingInfoDataSourceTest;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BinanceFundingInfoDataSourceTest extends AbstractFundingInfoDataSourceTest<BinanceFundingInfoDataSource> {
    private TradingPairSymbolRegistry symbolRegistry;
    private RestAssistant mockRestAssistant;
    private TaskScheduler mockTaskScheduler;

    protected BinanceFundingInfoDataSourceTest() {
        super("USDT");
    }

    @Override
    protected BinanceFundingInfoDataSource createDataSource(WsAssistant wsAssistant, IoExecutor ioExecutor) {
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT", "ETH-USDT", "ETHUSDT"));
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        mockTaskScheduler = Mockito.mock(TaskScheduler.class);
        return new BinanceFundingInfoDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_PUBLIC_URL,
                objectMapper,
                ioExecutor,
                symbolRegistry,
                mockRestAssistant,
                mockTaskScheduler
        );
    }

    @Override
    protected JsonNode createFundingInfoMessage(String tradingPair, String markPrice, String fundingRate, long nextFundingTime) {
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("e", "markPriceUpdate");
        msg.put("E", Instant.now().toEpochMilli());
        msg.put("s", symbol);
        msg.put("p", markPrice);
        msg.put("r", fundingRate);
        msg.put("T", nextFundingTime);
        return msg;
    }

    @Override
    protected WsResponse createAckResponse() {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.putNull("result");
        msg.put("id", 1);
        return new WsResponse(objectMapper.writeValueAsString(msg), WsResponse.MessageType.TEXT);
    }

    @Override
    protected WsResponse createErrorResponse(String errorMsg) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", 2);
        error.put("msg", errorMsg);
        ObjectNode msg = objectMapper.createObjectNode();
        msg.set("error", error);
        return new WsResponse(objectMapper.writeValueAsString(msg), WsResponse.MessageType.TEXT);
    }


    @Test
    @DisplayName("REST API로 펀딩 정보를 조회한다")
    void getsFundingInfo() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("symbol", "BTCUSDT");
        response.put("markPrice", "50000.00");
        response.put("lastFundingRate", "0.0001");
        response.put("nextFundingTime", 1700000000000L);
        response.put("time", 1699999000000L);

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        FundingInfoMessage result = fundingInfoDataSource.getFundingInfo("BTC-USDT");

        assertThat(result.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(result.nextFundingTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
    }

    @Test
    @DisplayName("올바른 심볼로 API를 호출한다")
    void callsWithCorrectSymbol() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("symbol", "ETHUSDT");
        response.put("markPrice", "3000.00");
        response.put("lastFundingRate", "0.0002");
        response.put("nextFundingTime", 1700000000000L);
        response.put("time", 1699999000000L);

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        fundingInfoDataSource.getFundingInfo("ETH-USDT");

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().params()).containsEntry("symbol", "ETHUSDT");
    }

    @Test
    @DisplayName("초기화 시 fundingInfo REST API를 호출하여 interval 캐시를 로드한다")
    void loadsFundingIntervalCacheOnStart() {
        ArrayNode fundingResponse = createFundingInfoListResponse(Map.of(
                "BTCUSDT", 8,
                "ETHUSDT", 4
        ));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(fundingResponse);

        fundingInfoDataSource.start();

        List<RestRequest> requests = captureAllRestRequests();
        assertThat(requests).anyMatch(req -> req.method() == HttpMethod.GET && req.pathUrl().equals(DerivativeApiSpec.FUNDING_INFO_PATH_URL));
    }

    @Test
    @DisplayName("WebSocket 메시지 파싱 시 캐시된 fundingInterval을 사용한다")
    void usesIntervalFromCacheWhenParsingWsMessage() {
        // BTCUSDT는 4시간, ETHUSDT는 1시간 - default 8시간과 다름
        ArrayNode fundingResponse = createFundingInfoListResponse(Map.of(
                "BTCUSDT", 4,
                "ETHUSDT", 1
        ));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class))).thenReturn(fundingResponse);
        fundingInfoDataSource.start();

        // WS 메시지로 들어오는 것 시뮬레이션
        JsonNode wsMsg = createFundingInfoMessage("BTC-USDT", "50000.00", "0.0001", 1700000000000L);
        FundingInfoMessage parsed = fundingInfoDataSource.parseFundingInfoMessage(wsMsg);
        assertThat(parsed.fundingInterval()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    @DisplayName("캐시에 없는 심볼은 기본 8시간 interval을 사용한다")
    void usesDefaultIntervalForUnknownSymbol() {
        // BTCUSDT만 cache에 등록
        ArrayNode fundingResponse = createFundingInfoListResponse(Map.of("BTCUSDT", 4));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(fundingResponse);

        fundingInfoDataSource.start();

        JsonNode wsMsg = createFundingInfoMessage("ETH-USDT", "3000.00", "0.0002", 1700000000000L);
        FundingInfoMessage parsed = fundingInfoDataSource.parseFundingInfoMessage(wsMsg);

        assertThat(parsed.fundingInterval()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("getFundingInfo (REST) 호출 시도 캐시된 interval을 사용한다")
    void getFundingInfoUsesIntervalCache() {
        ArrayNode fundingInfoResponse = createFundingInfoListResponse(Map.of("BTCUSDT", 4));
        ObjectNode markPriceResponse = objectMapper.createObjectNode();
        markPriceResponse.put("symbol", "BTCUSDT");
        markPriceResponse.put("markPrice", "50000.00");
        markPriceResponse.put("lastFundingRate", "0.0001");
        markPriceResponse.put("nextFundingTime", 1700000000000L);
        markPriceResponse.put("time", 1699999000000L);

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenAnswer(invocation -> {
                    RestRequest req = invocation.getArgument(0);
                    if (req.pathUrl().equals(DerivativeApiSpec.FUNDING_INFO_PATH_URL)) {
                        return fundingInfoResponse;
                    }

                    if (req.pathUrl().equals(DerivativeApiSpec.MARK_PRICE_PATH_URL)) {
                        return markPriceResponse;
                    }
                    throw new AssertionError("Unexpected path: " + req.pathUrl());
                });

        fundingInfoDataSource.start();
        FundingInfoMessage result = fundingInfoDataSource.getFundingInfo("BTC-USDT");

        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    @DisplayName("정시+1분 시점에 첫 갱신 task가 등록된다")
    void schedulesRefreshTaskAtNextHourPlusOneMinute() {
        ArrayNode fundingResponse = createFundingInfoListResponse(Map.of("BTCUSDT", 8));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(fundingResponse);
        when(mockTaskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(Mockito.mock(ScheduledFuture.class));

        fundingInfoDataSource.start();

        ArgumentCaptor<Instant> startTimeCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> periodCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(mockTaskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                startTimeCaptor.capture(),
                periodCaptor.capture());

        // 등록된 시작 시점이 정시 + 1분 형태인지
        ZonedDateTime scheduledTime = startTimeCaptor.getValue().atZone(ZoneOffset.UTC);
        assertThat(scheduledTime.getMinute()).isEqualTo(1);
        assertThat(scheduledTime.getSecond()).isEqualTo(0);

        // 1시간 주기인지
        assertThat(periodCaptor.getValue()).isEqualTo(Duration.ofHours(1));
    }




    private RestRequest captureRestRequest() {
        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant, atLeastOnce())
                .executeRequestAndGetJsonBody(captor.capture());
        return captor.getValue();
    }

    private List<RestRequest> captureAllRestRequests() {
        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant, atLeastOnce())
                .executeRequestAndGetJsonBody(captor.capture());
        return captor.getAllValues();
    }

    private ArrayNode createFundingInfoListResponse(Map<String, Integer> symbolToHours) {
        ArrayNode arr = objectMapper.createArrayNode();
        symbolToHours.forEach((symbol, hours) -> {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("symbol", symbol);
            entry.put("fundingIntervalHours", hours);
            arr.add(entry);
        });
        return arr;
    }

}