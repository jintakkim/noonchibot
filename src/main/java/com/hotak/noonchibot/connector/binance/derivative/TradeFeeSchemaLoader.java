package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import com.hotak.noonchibot.core.trade.TradeFeeSchema;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

class TradeFeeSchemaLoader extends AbstractTradeFeeSchemaLoader {
    private final RestAssistant restAssistant;

    public TradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant
    ) {
        super(ioExecutor, tradingPairSymbolRegistry);
        this.restAssistant = restAssistant;
    }

    public TradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                ioExecutor,
                tradingPairSymbolRegistry,
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.tradeFee(Exchange.BINANCE_DERIVATIVE))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .maxRetry(2)
                        .build()
        );
    }

    protected JsonNode fetchPairFeeSchema(String symbol) {
        return restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.COMMISSION_RATE_PATH_URL)
                        .params(Map.of("symbol", symbol))
                        .authRequired(true)
                        .build()
        );
    }

    @Override
    protected TradeFeeSchema parseSchema(JsonNode schema) {
        return new TradeFeeSchema(
                null,
                schema.get("makerCommissionRate").asDecimal(),
                schema.get("takerCommissionRate").asDecimal(),
                false,
                List.of(),
                List.of()
        );
    }
}
