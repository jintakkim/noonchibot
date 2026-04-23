package com.hotak.noonchibot.connector.bybit;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FundingInfoDataSourceTest extends AbstractFundingInfoDataSourceTest {
    private TradingPairSymbolRegistry symbolRegistry;
    private RestAssistant mockRestAssistant;

    @Override
    protected AbstractFundingInfoDataSource createDataSource(WsAssistant wsAssistant, IoExecutor ioExecutor) {
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT", "ETH-USDT", "ETHUSDT"));
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        return new FundingInfoDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_LINEAR_URL,
                objectMapper,
                ioExecutor,
                symbolRegistry,
                mockRestAssistant
        );
    }

    @Override
    protected JsonNode createFundingInfoMessage(String tradingPair, String markPrice, String fundingRate, long nextFundingTime) {
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("topic", "tickers." + symbol);
        wrapper.put("type", "snapshot");
        wrapper.put("ts", Instant.now().toEpochMilli());
        wrapper.put("cs", 100L);

        ObjectNode data = objectMapper.createObjectNode();
        data.put("symbol", symbol);
        data.put("markPrice", markPrice);
        data.put("fundingRate", fundingRate);
        data.put("nextFundingTime", nextFundingTime);

        wrapper.set("data", data);
        return wrapper;
    }

    @Override
    protected WsResponse createAckResponse() {
        ObjectNode ack = objectMapper.createObjectNode();
        ack.put("success", true);
        ack.put("ret_msg", "");
        ack.put("op", "subscribe");
        ack.put("conn_id", "test-conn-id");
        return new WsResponse(ack.toString(), WsResponse.MessageType.TEXT);
    }

    @Override
    protected WsResponse createErrorResponse(String errorMsg) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("ret_msg", errorMsg);
        error.put("op", "subscribe");
        return new WsResponse(error.toString(), WsResponse.MessageType.TEXT);
    }

    @Test
    @DisplayName("REST API로 펀딩 정보를 조회한다")
    void getsFundingInfo() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("retCode", 0);
        response.put("retMsg", "OK");
        response.put("time", 1699999000000L);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode list = result.putArray("list");
        ObjectNode ticker = list.addObject();
        ticker.put("symbol", "BTCUSDT");
        ticker.put("markPrice", "50000.00");
        ticker.put("fundingRate", "0.0001");
        ticker.put("nextFundingTime", 1700000000000L);

        response.set("result", result);

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        FundingInfoMessage info = dataSource.getFundingInfo("BTC-USDT");

        assertThat(info.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(info.markPrice()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(info.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(info.nextFundingTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
    }

    @Test
    @DisplayName("올바른 심볼과 category로 API를 호출한다")
    void callsWithCorrectSymbol() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("retCode", 0);
        response.put("time", 1699999000000L);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode list = result.putArray("list");
        ObjectNode ticker = list.addObject();
        ticker.put("symbol", "ETHUSDT");
        ticker.put("markPrice", "3000.00");
        ticker.put("fundingRate", "0.0002");
        ticker.put("nextFundingTime", 1700000000000L);

        response.set("result", result);

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        dataSource.getFundingInfo("ETH-USDT");

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().params())
                .containsEntry("symbol", "ETHUSDT")
                .containsEntry("category", "linear");
    }
}