package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BybitFundingInfoDataSourceTest extends AbstractWsFundingInfoDataSourceTest<BybitWsFundingInfoDataSource> {

    private TradingPairSymbolRegistry symbolRegistry;
    private RestAssistantImpl mockRestAssistant;

    protected BybitFundingInfoDataSourceTest() {
        super("USDT");
    }

    @Override
    protected BybitWsFundingInfoDataSource createDataSource(
            WsAssistantImpl wsAssistant,
            IoExecutor ioExecutor
    ) {
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
        mockRestAssistant = Mockito.mock(RestAssistantImpl.class);
        return new BybitWsFundingInfoDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_LINEAR_URL,
                objectMapper,
                ioExecutor,
                symbolRegistry,
                mockRestAssistant
        );
    }

    @Override
    protected JsonNode createFundingInfoMessage(
            String tradingPair, String markPrice, String fundingRate, long nextFundingTime) {
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        // Bybit ticker WS 메시지 형식
        ObjectNode data = objectMapper.createObjectNode();
        data.put("symbol", symbol);
        data.put("markPrice", markPrice);
        data.put("fundingRate", fundingRate);
        data.put("fundingIntervalHour", "8");  // 기본 8시간
        data.put("nextFundingTime", String.valueOf(nextFundingTime));

        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("topic", "tickers." + symbol);
        msg.put("type", "snapshot");
        msg.put("ts", Instant.now().toEpochMilli());
        msg.set("data", data);
        return msg;
    }

    @Override
    protected WsResponse createAckResponse() {
        // {"success": true, "ret_msg": "subscribe", "conn_id": "...", "op": "subscribe"}
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("success", true);
        msg.put("op", "subscribe");
        return new WsResponse(objectMapper.writeValueAsString(msg), WsResponse.MessageType.TEXT);
    }

    @Override
    protected WsResponse createErrorResponse(String errorMsg) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("success", false);
        msg.put("ret_msg", errorMsg);
        msg.put("op", "subscribe");
        return new WsResponse(objectMapper.writeValueAsString(msg), WsResponse.MessageType.TEXT);
    }

    @Test
    @DisplayName("REST API로 펀딩 정보를 조회한다")
    void getsFundingInfoFromTickers() {
        ObjectNode response = createTickersResponse(
                "BTCUSDT", "50000.00", "0.0001", 1700000000000L, "8");

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        FundingInfoMessage result = fundingInfoDataSource.getFundingInfo("BTC-USDT");

        assertThat(result.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(result.nextFundingTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("REST 호출 시 category=linear와 올바른 symbol로 호출한다")
    void callsTickersWithLinearCategoryAndCorrectSymbol() {
        ObjectNode response = createTickersResponse(
                "ETHUSDT", "3000.00", "0.0002", 1700000000000L, "4");
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        fundingInfoDataSource.getFundingInfo("ETH-USDT");

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());

        RestRequest req = captor.getValue();
        assertThat(req.method()).isEqualTo(HttpMethod.GET);
        assertThat(req.pathUrl()).isEqualTo(DerivativeApiSpec.TICKER_PRICE_CHANGE_PATH_URL);
        assertThat(req.params())
                .containsEntry("category", "linear")
                .containsEntry("symbol", "ETHUSDT");
    }

    @Test
    @DisplayName("페어별로 다른 fundingIntervalHour를 정확히 반영한다")
    void reflectsDifferentFundingIntervalsPerPair() {
        // ETHUSDT는 4시간 인터벌
        ObjectNode response = createTickersResponse(
                "ETHUSDT", "3000.00", "0.0001", 1700000000000L, "4");
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        FundingInfoMessage result = fundingInfoDataSource.getFundingInfo("ETH-USDT");

        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    @DisplayName("WS 메시지 파싱 시 fundingIntervalHour가 정확히 Duration으로 변환된다")
    void parsesIntervalFromWsMessage() {
        JsonNode wsMsg = createFundingInfoMessage(
                "BTC-USDT", "50000.00", "0.0001", 1700000000000L);

        FundingInfoMessage parsed = fundingInfoDataSource.parseFundingInfoMessage(wsMsg);

        assertThat(parsed.fundingInterval()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("WS 메시지에 1시간 인터벌이 들어와도 정확히 파싱한다")
    void parsesHourlyInterval() {
        // 일부 변동성 큰 페어는 1시간 인터벌
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol("BTC-USDT");
        ObjectNode data = objectMapper.createObjectNode();
        data.put("symbol", symbol);
        data.put("markPrice", "50000");
        data.put("fundingRate", "0.0005");
        data.put("nextFundingTime", "1700000000000");
        data.put("fundingIntervalHour", "1");

        ObjectNode wsMsg = objectMapper.createObjectNode();
        wsMsg.put("type", "snapshot");
        wsMsg.put("topic", "tickers." + symbol);
        wsMsg.put("ts", Instant.now().toEpochMilli());
        wsMsg.set("data", data);

        FundingInfoMessage parsed = fundingInfoDataSource.parseFundingInfoMessage(wsMsg);

        assertThat(parsed.fundingInterval()).isEqualTo(Duration.ofHours(1));
    }


    /**
     * Bybit tickers REST 응답 형식을 흉내냄.
     * {"retCode": 0, "retMsg": "OK", "result": {"category": "linear", "list": [...]}, "time": ...}
     */
    private ObjectNode createTickersResponse(
            String symbol, String markPrice, String fundingRate,
            long nextFundingTime, String fundingIntervalHour) {
        ObjectNode ticker = objectMapper.createObjectNode();
        ticker.put("symbol", symbol);
        ticker.put("markPrice", markPrice);
        ticker.put("fundingRate", fundingRate);
        ticker.put("nextFundingTime", String.valueOf(nextFundingTime));
        ticker.put("fundingIntervalHour", fundingIntervalHour);

        ArrayNode list = objectMapper.createArrayNode();
        list.add(ticker);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("category", "linear");
        result.set("list", list);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("retCode", 0);
        response.put("retMsg", "OK");
        response.set("result", result);
        response.put("time", Instant.now().toEpochMilli());
        return response;
    }
}