package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.AbstractFundingInfoDataSource;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FundingInfoDataSource extends AbstractFundingInfoDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("subscribe"),
        UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;

    public FundingInfoDataSource(
            WsAssistant wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant
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
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(
                data.get("symbol").asString()
        );

        return new FundingInfoMessage(
                tradingPair,
                Instant.ofEpochMilli(msg.get("ts").asLong()),
                data.get("markPrice").asDecimal(),
                data.get("fundingRate").asDecimal(),
                Instant.ofEpochMilli(data.get("nextFundingTime").asLong()),
                null
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
                                "category", "linear",
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
                null
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

