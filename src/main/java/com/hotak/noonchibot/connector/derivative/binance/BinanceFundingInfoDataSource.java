package com.hotak.noonchibot.connector.derivative.binance;

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
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BinanceFundingInfoDataSource extends AbstractFundingInfoDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;

    public BinanceFundingInfoDataSource(
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
    protected void sendSubscribe(String tradingPair) {
        sendFundingRateRequest(MessageMethod.SUBSCRIBE, Set.of(tradingPair));
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendFundingRateRequest(MessageMethod.SUBSCRIBE, tradingPairs);
    }

    @Override
    protected void sendUnsubscribe(String tradingPair) {
        sendFundingRateRequest(MessageMethod.UNSUBSCRIBE, Set.of(tradingPair));
    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        return msg.has("error");
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        return msg.has("id") && msg.has("result");
    }

    @Override
    protected FundingInfoMessage parseFundingInfoMessage(JsonNode msg) {
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(msg.get("s").asString());
        return new FundingInfoMessage(
                tradingPair,
                Instant.ofEpochMilli(msg.get("E").asLong()),
                msg.get("p").asDecimal(),
                msg.get("r").asDecimal(),
                Instant.ofEpochMilli(msg.get("T").asLong())
        );
    }

    @Override
    public FundingInfoMessage getFundingInfo(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode data = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(BinanceDerivativeApiSpec.MARK_PRICE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .build()
        );
        return new FundingInfoMessage(
                tradingPair,
                Instant.ofEpochMilli(data.get("time").asLong()),
                data.get("markPrice").asDecimal(),
                data.get("lastFundingRate").asDecimal(),
                Instant.ofEpochMilli(data.get("nextFundingTime").asLong())
        );
    }

    private void sendFundingRateRequest(MessageMethod method, Collection<String> tradingPairs) {
        List<String> params = tradingPairs.stream()
                .map(tradingPairSymbolRegistry::convertTradingPairToExchangeSymbol)
                .map(String::toLowerCase)
                .map(symbol -> symbol + "@markPrice")
                .toList();
        wsConnection.send(new WsRequest(Map.of(
                "method", method.getApiValue(),
                "params", params,
                "id", 1
        ), false));
    }
}
