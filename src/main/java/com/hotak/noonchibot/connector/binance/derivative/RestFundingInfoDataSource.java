package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.*;

@Slf4j
record RestFundingInfoDataSource(
        TradingPairSymbolRegistry tradingPairSymbolRegistry,
        RestAssistant restAssistant,
        EventPublisher eventPublisher
) implements FailureAwareEventHandler<FundingInfoEvent.RestFetchRequested> {

    @Override
    public void onEvent(FundingInfoEvent.RestFetchRequested event) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(event.tradingPair());
        JsonNode data = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.MARK_PRICE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .build()
        );

        eventPublisher.publish(new FundingInfoEvent.Received(
                event.tradingPair(),
                Instant.ofEpochMilli(data.get("time").asLong()),
                data.get("markPrice").asDecimal(),
                data.get("lastFundingRate").asDecimal(),
                Instant.ofEpochMilli(data.get("nextFundingTime").asLong()),
                null
        ));
    }

    @Override
    public void onFailure(FundingInfoEvent.RestFetchRequested event, Throwable cause) {
        log.error("funding info fetch failed", cause);
        eventPublisher.publish(new FundingInfoEvent.RestFetchFailed(cause));
    }
}
