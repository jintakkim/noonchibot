package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.PriceCandleDataSource;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.pricegap.history.PriceCandle;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HyperliquidPriceCandleDataSource implements PriceCandleDataSource {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry symbols;

    public HyperliquidPriceCandleDataSource(RestAssistant restAssistant, TradingPairSymbolRegistry symbols) {
        this.restAssistant = restAssistant;
        this.symbols = symbols;
    }

    @Override
    public Exchange exchange() {
        return Exchange.HYPERLIQUID_DERIVATIVE;
    }

    @Override
    public List<PriceCandle> fetch(String tradingPair, Duration interval, Instant from, Instant to) {
        String coin = symbols.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of(
                        "type", "candleSnapshot",
                        "req", Map.of(
                                "coin", coin,
                                "interval", interval(interval),
                                "startTime", from.toEpochMilli(),
                                "endTime", to.toEpochMilli()
                        )
                ))
                .build());
        List<PriceCandle> result = new ArrayList<>();
        for (JsonNode item : response) {
            result.add(new PriceCandle(
                    exchange(),
                    tradingPair,
                    Instant.ofEpochMilli(item.get("t").asLong()),
                    item.get("c").asDecimal()
            ));
        }
        return List.copyOf(result);
    }

    private String interval(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) return minutes + "m";
        return duration.toHours() + "h";
    }
}
