package com.hotak.noonchibot.connector.binance;

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

public class BinancePriceCandleDataSource implements PriceCandleDataSource {
    private static final int LIMIT = 1000;

    private final Exchange exchange;
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry symbols;
    private final String path;

    public BinancePriceCandleDataSource(
            Exchange exchange,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols,
            String path
    ) {
        this.exchange = exchange;
        this.restAssistant = restAssistant;
        this.symbols = symbols;
        this.path = path;
    }

    @Override
    public Exchange exchange() {
        return exchange;
    }

    @Override
    public List<PriceCandle> fetch(String tradingPair, Duration interval, Instant from, Instant to) {
        String symbol = symbols.convertTradingPairToExchangeSymbol(tradingPair);
        List<PriceCandle> result = new ArrayList<>();
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                    .method(HttpMethod.GET)
                    .pathUrl(path)
                    .params(Map.of(
                            "symbol", symbol,
                            "interval", interval(interval),
                            "startTime", cursor.toEpochMilli(),
                            "endTime", to.toEpochMilli(),
                            "limit", LIMIT
                    ))
                    .build());
            int count = 0;
            Instant last = null;
            for (JsonNode item : response) {
                Instant timestamp = Instant.ofEpochMilli(item.get(0).asLong());
                result.add(new PriceCandle(exchange, tradingPair, timestamp, item.get(4).asDecimal()));
                last = timestamp;
                count++;
            }
            if (count < LIMIT || last == null) break;
            cursor = last.plus(interval);
        }
        return List.copyOf(result);
    }

    private String interval(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) return minutes + "m";
        return duration.toHours() + "h";
    }
}
