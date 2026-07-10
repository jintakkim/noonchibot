package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.FundingRateHistoryDataSource;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.funding.FundingRatePoint;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class FundingRateHistoryDataSourceImpl implements FundingRateHistoryDataSource {
    private static final int LIMIT = 1000;

    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry symbolRegistry;

    FundingRateHistoryDataSourceImpl(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbolRegistry
    ) {
        this.restAssistant = restAssistant;
        this.symbolRegistry = symbolRegistry;
    }

    @Override
    public List<FundingRatePoint> fetch(String tradingPair, Instant from, Instant to) {
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.FUNDING_RATE_HISTORY_PATH_URL)
                .params(Map.of(
                        "symbol", symbol,
                        "startTime", from.toEpochMilli(),
                        "endTime", to.toEpochMilli(),
                        "limit", LIMIT
                ))
                .build());

        List<RawFundingRate> raw = new ArrayList<>();
        response.forEach(item -> raw.add(new RawFundingRate(
                Instant.ofEpochMilli(item.get("fundingTime").asLong()),
                item.get("fundingRate").asDecimal()
        )));
        raw.sort(Comparator.comparing(RawFundingRate::time));

        List<FundingRatePoint> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            RawFundingRate current = raw.get(i);
            Duration interval = interval(raw, i, ApiSpec.DEFAULT_FUNDING_INTERVAL);
            result.add(new FundingRatePoint(
                    Exchange.BINANCE_DERIVATIVE,
                    tradingPair,
                    current.time(),
                    current.rate(),
                    interval
            ));
        }
        return List.copyOf(result);
    }

    private Duration interval(List<RawFundingRate> rates, int index, Duration fallback) {
        if (index > 0) {
            return Duration.between(rates.get(index - 1).time(), rates.get(index).time());
        }
        if (rates.size() > 1) {
            return Duration.between(rates.getFirst().time(), rates.get(1).time());
        }
        return fallback;
    }

    private record RawFundingRate(Instant time, java.math.BigDecimal rate) {}
}
