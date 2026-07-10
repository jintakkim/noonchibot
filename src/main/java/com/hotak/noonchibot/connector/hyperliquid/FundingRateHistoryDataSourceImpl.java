package com.hotak.noonchibot.connector.hyperliquid;

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
import java.util.List;
import java.util.Map;

final class FundingRateHistoryDataSourceImpl implements FundingRateHistoryDataSource {
    private static final int PAGE_LIMIT = 500;
    private static final Duration FUNDING_INTERVAL = Duration.ofHours(1);

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
        String coin = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        List<FundingRatePoint> result = new ArrayList<>();
        Instant cursor = from;

        while (!cursor.isAfter(to)) {
            JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                    .method(HttpMethod.POST)
                    .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                    .body(Map.of(
                            "type", "fundingHistory",
                            "coin", coin,
                            "startTime", cursor.toEpochMilli(),
                            "endTime", to.toEpochMilli()
                    ))
                    .build());

            int count = 0;
            Instant lastTime = null;
            for (JsonNode item : response) {
                Instant fundingTime = Instant.ofEpochMilli(item.get("time").asLong());
                result.add(new FundingRatePoint(
                        Exchange.HYPERLIQUID_DERIVATIVE,
                        tradingPair,
                        fundingTime,
                        item.get("fundingRate").asDecimal(),
                        FUNDING_INTERVAL
                ));
                lastTime = fundingTime;
                count++;
            }
            if (count < PAGE_LIMIT || lastTime == null || !lastTime.isBefore(to)) break;
            cursor = lastTime.plusMillis(1);
        }
        return List.copyOf(result);
    }
}
