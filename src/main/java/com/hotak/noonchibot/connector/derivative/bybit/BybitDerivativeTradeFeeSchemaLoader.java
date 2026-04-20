package com.hotak.noonchibot.connector.derivative.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class BybitDerivativeTradeFeeSchemaLoader implements TradeFeeSchemaLoader, SmartLifecycle {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry symbolRegistry;
    private final Map<String, TradeFeeSchema> cache = new HashMap<>();
    private volatile boolean running = false;


    @Override
    public TradeFeeSchema get(String tradingPair) {
        TradeFeeSchema schema = cache.get(tradingPair);
        if (schema == null) throw new IllegalStateException("Fee schema not loaded for: " + tradingPair);
        return schema;
    }

    private TradeFeeSchema fetchPairFeeSchema(String tradingPair) {
        String symbol = symbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(BybitDerivativeApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of(
                                "category", "linear",
                                "symbol", symbol
                        ))
                        .authRequired(true)
                        .build()
        );

        int retCode = response.path("retCode").asInt(-1);
        if (retCode != 0) {
            throw new IllegalStateException(
                    "Failed to fetch fee rate: " + response.path("retMsg").asString()
            );
        }

        JsonNode list = response.path("result").path("list");
        if (!list.isArray() || list.isEmpty()) {
            throw new IllegalStateException("No fee rate data for symbol: " + symbol);
        }

        JsonNode feeData = list.get(0);

        return new TradeFeeSchema(
                null,
                feeData.get("makerFeeRate").asDecimal(),
                feeData.get("takerFeeRate").asDecimal(),
                false,
                List.of(),
                List.of()
        );
    }

    @Override
    public void start() {
        symbolRegistry.getAllTradingPairs().forEach(pair -> cache.put(pair, fetchPairFeeSchema(pair)));
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
