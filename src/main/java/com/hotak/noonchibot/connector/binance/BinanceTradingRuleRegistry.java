package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.AbstractPollingTradingRuleRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

public class BinanceTradingRuleRegistry extends AbstractPollingTradingRuleRegistry {
    private final TradingPairSymbolRegistry symbolRegistry;
    private final String requestPath;
    private final ObjectMapper objectMapper;

    public BinanceTradingRuleRegistry(
            RestAssistant restAssistant,
            TradingRuleParser parser,
            TaskScheduler scheduler,
            Duration pollingInterval,
            TradingPairSymbolRegistry symbolRegistry,
            String requestPath,
            ObjectMapper objectMapper
    ) {
        super(restAssistant, parser, scheduler, pollingInterval);
        this.symbolRegistry = symbolRegistry;
        this.requestPath = requestPath;
        this.objectMapper = objectMapper;
    }

    public BinanceTradingRuleRegistry(
            RestAssistant restAssistant,
            TradingRuleParser parser,
            TaskScheduler scheduler,
            Duration pollingInterval,
            TradingPairSymbolRegistry symbolRegistry,
            String requestPath,
            ObjectMapper objectMapper,
            Exchange exchange,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.tradingRules(exchange))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                parser,
                scheduler,
                pollingInterval,
                symbolRegistry,
                requestPath,
                objectMapper
        );
    }

    @Override
    protected RestRequest createRequest() {
        String symbols = objectMapper.writeValueAsString(symbolRegistry.getAllExchangeSymbols());
        return RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(requestPath)
                .params(Map.of("symbols", symbols))
                .build();
    }
}
