package com.hotak.noonchibot.connector.derivative.binance;

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
import org.springframework.core.task.AsyncTaskExecutor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BinanceFundingInfoDataSourceTest extends AbstractFundingInfoDataSourceTest {
    private TradingPairSymbolRegistry symbolRegistry;
    private RestAssistant mockRestAssistant;

    @Override
    protected AbstractFundingInfoDataSource createDataSource(WsAssistant wsAssistant, IoExecutor ioExecutor) {
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDT", "BTCUSDT", "ETH-USDT", "ETHUSDT"));
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        return new BinanceFundingInfoDataSource(
                wsAssistant,
                BinanceDerivativeApiSpec.WSS_PUBLIC_URL,
                objectMapper,
                ioExecutor,
                symbolRegistry,
                mockRestAssistant
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

        FundingInfoMessage result = dataSource.getFundingInfo("BTC-USDT");

        assertThat(result.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(result.nextFundingTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
    }

    @Test
    @DisplayName("올바른 심볼로 API를 호출한다")
    void callsWithCorrectSymbol() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("markPrice", "3000.00");
        response.put("lastFundingRate", "0.0002");
        response.put("nextFundingTime", 1700000000000L);
        response.put("time", 1699999000000L);

        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        dataSource.getFundingInfo("ETH-USDT");

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().params()).containsEntry("symbol", "ETHUSDT");
    }

}