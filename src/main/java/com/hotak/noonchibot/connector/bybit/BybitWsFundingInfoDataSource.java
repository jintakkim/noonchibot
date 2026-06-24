package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.derivative.AbstractWsFundingInfoDataSource;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
public class BybitWsFundingInfoDataSource extends AbstractWsFundingInfoDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("subscribe"),
        UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }
    private static final String CATEGORY_LINEAR = "linear";

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistantImpl restAssistant;

    public BybitWsFundingInfoDataSource(
            WsAssistantImpl wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistantImpl restAssistant
    ) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.restAssistant = restAssistant;
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendFundingRateRequest(MessageMethod.SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(Set<String> tradingPairs) {
        sendFundingRateRequest(MessageMethod.UNSUBSCRIBE, tradingPairs);
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        return msg.has("success") && !msg.get("success").asBoolean();
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("op") && msg.has("success");
    }

    @Override
    protected FundingInfoMessage parseFundingInfoMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        String type = msg.get("type").asString();
        String exchangeSymbol = data.get("symbol").asString();
        String tradingPair = tradingPairSymbolRegistry
                .convertExchangeSymbolToTradingPair(exchangeSymbol);
        Instant ts =  Instant.ofEpochMilli(msg.get("ts").asLong());
        BigDecimal markPrice = null, fundingRate = null;
        Instant nextFundingTime = null;
        Duration fundingIntervalHour = null;
        if(data.has("markPrice")) markPrice = data.get("markPrice").asDecimal();
        if(data.has("fundingRate")) fundingRate = data.get("fundingRate").asDecimal();
        if(data.has("nextFundingTime")) nextFundingTime = Instant.ofEpochMilli(data.get("nextFundingTime").asLong());
        if(data.has("fundingIntervalHour")) fundingIntervalHour =  Duration.ofHours(Long.parseLong(data.get("fundingIntervalHour").asString()));
        if(type.equals("snapshot")) {
            if(markPrice == null || fundingRate == null || nextFundingTime == null || fundingIntervalHour == null) {
                log.warn("Bybit ticker snapshot is missing required fields: tradingPair={}, markPrice={}, fundingRate={}, nextFundingTime={}, fundingInterval={}, raw={}",
                        tradingPair, markPrice, fundingRate, nextFundingTime, fundingIntervalHour, msg);
            }
        }
        return new FundingInfoMessage(
                tradingPair,
                ts,
                markPrice,
                fundingRate,
                nextFundingTime,
                fundingIntervalHour
        );
    }

    @Override
    public FundingInfoMessage getFundingInfo(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(DerivativeApiSpec.TICKER_PRICE_CHANGE_PATH_URL)
                        .params(Map.of(
                                "category", CATEGORY_LINEAR,
                                "symbol", symbol
                        ))
                        .build()
        );

        JsonNode ticker = response.path("result").path("list").get(0);

        return new FundingInfoMessage(
                tradingPair,
                Instant.ofEpochMilli(response.get("time").asLong()),
                ticker.get("markPrice").asDecimal(),
                ticker.get("fundingRate").asDecimal(),
                Instant.ofEpochMilli(ticker.get("nextFundingTime").asLong()),
                Duration.ofHours(Long.parseLong(ticker.get("fundingIntervalHour").asString()))
        );
    }

    private void sendFundingRateRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> args = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(symbol -> "tickers." + symbol)
                .toList();

        wsConnection.send(new WsRequest(Map.of(
                "op", method.getApiValue(),
                "args", args
        ), false));
    }
}
