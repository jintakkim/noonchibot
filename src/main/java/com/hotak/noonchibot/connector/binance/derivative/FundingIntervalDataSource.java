package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.FailureAwareEventHandler;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public record FundingIntervalDataSource(
      RestAssistant restAssistant,
      TradingPairSymbolRegistry tradingPairSymbolRegistry,
      EventPublisher eventPublisher
) implements FailureAwareEventHandler<FundingInfoEvent.IntervalRestFetchRequested> {
    @Override
    public void onEvent(FundingInfoEvent.IntervalRestFetchRequested req) {
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.FUNDING_INFO_PATH_URL)
                        .build()
        );

        Map<String, Duration> snapshot = new HashMap<>();
        List<String> ignoredSymbols = new ArrayList<>();
        for (JsonNode entry : response) {
            String exchangeSymbol = entry.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(
                    exchangeSymbol,
                    false
            );
            if (tradingPair == null) {
                ignoredSymbols.add(exchangeSymbol);
                continue;
            }
            int hours = entry.get("fundingIntervalHours").asInt();
            snapshot.put(tradingPair, Duration.ofHours(hours));
        }
        if (!ignoredSymbols.isEmpty()) {
            log.info("Ignored unregistered funding interval symbols: {}", ignoredSymbols);
        }
        eventPublisher.publish(new FundingInfoEvent.IntervalReceived(Map.copyOf(snapshot)));
        log.debug("Loaded {} funding intervals", snapshot.size());
    }

    @Override
    public void onFailure(FundingInfoEvent.IntervalRestFetchRequested req, Throwable cause) {
        log.warn("Funding interval refresh failed, keeping existing cache", cause);
        eventPublisher.publish(new FundingInfoEvent.IntervalRestFetchFailed(cause));
    }
}
