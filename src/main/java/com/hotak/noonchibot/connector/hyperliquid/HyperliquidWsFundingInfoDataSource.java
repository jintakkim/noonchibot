package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.derivative.AbstractWsFundingInfoDataSource;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

class HyperliquidWsFundingInfoDataSource extends AbstractWsFundingInfoDataSource {
    @RequiredArgsConstructor
    private enum MessageMethod {
        SUBSCRIBE("subscribe"), UNSUBSCRIBE("unsubscribe");
        private final String apiValue;
    }

    private static final Duration FUNDING_INTERVAL = Duration.ofHours(1);

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;

    public HyperliquidWsFundingInfoDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            List<String> pairsToSubscribe
            ) {
        super(wsAssistant, objectMapper, ioExecutor, eventPublisher, pairsToSubscribe);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.restAssistant = restAssistant;
    }

    @Override
    protected URI connectionUri() {
        return URI.create(DerivativeApiSpec.WS_URL);
    }

    @Override
    protected void sendSubscribe(Set<String> tradingPairs) {
        sendSubscriptionRequest(MessageMethod.SUBSCRIBE, tradingPairs);

    }

    @Override
    protected void sendUnsubscribe(Set<String> tradingPairs) {
        sendSubscriptionRequest(MessageMethod.UNSUBSCRIBE, tradingPairs);

    }

    @Override
    protected boolean isErrorMessage(JsonNode msg) {
        JsonNode channel = msg.get("channel");
        return channel != null && "error".equals(channel.asString());
    }

    @Override
    protected boolean isAckMessage(JsonNode msg) {
        JsonNode channel = msg.get("channel");
        return channel != null && "subscriptionResponse".equals(channel.asString());
    }

    @Override
    protected FundingInfoMessage parseFundingInfoMessage(JsonNode msg) {
        JsonNode data = msg.get("data");
        JsonNode ctx = data.get("ctx");

        String coin = data.get("coin").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin);

        BigDecimal markPrice = ctx.get("markPx").asDecimal();
        BigDecimal hourlyFundingRate = ctx.get("funding").asDecimal();
        Instant eventTime = data.has("time")
                ? Instant.ofEpochMilli(data.get("time").asLong())
                : Instant.now();  // ctx에 timestamp 없음
        Instant nextFundingTime = computeNextFundingTime(eventTime);

        return new FundingInfoMessage(
                tradingPair,
                eventTime,
                markPrice,
                hourlyFundingRate,
                nextFundingTime,
                FUNDING_INTERVAL
        );
    }

    public FundingInfoMessage getFundingInfo(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of(
                                "type", "activeAssetCtx",
                                "coin", symbol
                        ))
                        .build()
        );

        JsonNode ctx = response.get("ctx");
        BigDecimal markPrice = ctx.get("markPx").asDecimal();
        BigDecimal hourlyFundingRate = ctx.get("funding").asDecimal();
        Instant now = Instant.now();

        return new FundingInfoMessage(
                tradingPair,
                now,
                markPrice,
                hourlyFundingRate,
                computeNextFundingTime(now),
                FUNDING_INTERVAL
        );
    }

    private void sendSubscriptionRequest(MessageMethod method, Set<String> tradingPairs) {
        for (String tradingPair : tradingPairs) {
            String coin = tradingPairSymbolRegistry
                    .convertTradingPairToExchangeSymbol(tradingPair);
            wsConnection.send(new WsRequest(Map.of(
                    "method", method.apiValue,
                    "subscription", Map.of(
                            "type", "activeAssetCtx",
                            "coin", coin
                    )
            ), false));
        }
    }

    /**
     * Hyperliquid 펀딩은 매 정시(UTC) 발생. 다음 정시를 계산.
     */
    private Instant computeNextFundingTime(Instant now) {
        return now.truncatedTo(ChronoUnit.HOURS).plus(FUNDING_INTERVAL);
    }
}
