package com.hotak.noonchibot.connector.derivative.binance;

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
public class BinanceDerivativeTradeFeeSchemaLoader implements TradeFeeSchemaLoader, SmartLifecycle {
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
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(BinanceDerivativeApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .authRequired(true)
                        .build()
        );
        return new TradeFeeSchema(
                null,
                body.get("makerCommissionRate").asDecimal(),
                body.get("takerCommissionRate").asDecimal(),
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
