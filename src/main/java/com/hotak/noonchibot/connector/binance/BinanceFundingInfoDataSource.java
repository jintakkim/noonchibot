package com.hotak.noonchibot.connector.binance;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class BinanceFundingInfoDataSource extends AbstractFundingInfoDataSource {
    @RequiredArgsConstructor
    @Getter
    private enum MessageMethod {
        SUBSCRIBE("SUBSCRIBE"), UNSUBSCRIBE("UNSUBSCRIBE");
        private final String apiValue;
    }

    private static final Duration DEFAULT_FUNDING_INTERVAL = Duration.ofHours(8);

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;
    private final TaskScheduler taskScheduler;
    private volatile Map<String, Duration> fundingIntervalCache = Map.of();
    private volatile ScheduledFuture<?> refreshTask;

    public BinanceFundingInfoDataSource(
            WsAssistant wsAssistant,
            String publicWsUrl,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            TaskScheduler taskScheduler
    ) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor);
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.restAssistant = restAssistant;
        this.taskScheduler = taskScheduler;
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
                Instant.ofEpochMilli(msg.get("T").asLong()),
                resolveInterval(msg.get("s").asString())
        );
    }

    @Override
    public FundingInfoMessage getFundingInfo(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode data = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(DerivativeApiSpec.MARK_PRICE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .build()
        );
        return new FundingInfoMessage(
                tradingPair,
                Instant.ofEpochMilli(data.get("time").asLong()),
                data.get("markPrice").asDecimal(),
                data.get("lastFundingRate").asDecimal(),
                Instant.ofEpochMilli(data.get("nextFundingTime").asLong()),
                resolveInterval(data.get("symbol").asString())
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

    private void loadAllFundingIntervals() {
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(DerivativeApiSpec.FUNDING_INFO_PATH_URL)
                        .build()
        );
        Map<String, Duration> newCache = new HashMap<>();
        for (JsonNode entry : response) {
            String symbol = entry.get("symbol").asString();
            int hours = entry.get("fundingIntervalHours").asInt();
            newCache.put(symbol, Duration.ofHours(hours));
        }
        fundingIntervalCache = Map.copyOf(newCache);
        log.info("Loaded {} funding intervals", newCache.size());
    }

    private Duration resolveInterval(String symbol) {
        return fundingIntervalCache.getOrDefault(symbol, DEFAULT_FUNDING_INTERVAL);
    }

    @Override
    public void start() {
        super.start();
        loadAllFundingIntervals();
        Instant now = Instant.now();
        Instant nextRefresh = now.atZone(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .plusMinutes(1)
                .toInstant();
        refreshTask = taskScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        loadAllFundingIntervals();
                    } catch (Exception e) {
                        log.warn("Funding interval refresh failed, keeping existing cache", e);
                    }
                },
                nextRefresh,
                Duration.ofHours(1)
        );
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (refreshTask != null) {
            refreshTask.cancel(true);
        }
    }
}
