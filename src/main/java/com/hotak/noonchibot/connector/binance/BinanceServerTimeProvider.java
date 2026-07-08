package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.ServerTimeProvider;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

@RequiredArgsConstructor
public class BinanceServerTimeProvider implements ServerTimeProvider {
    private final RestAssistant restAssistant;
    private final String pathUrl;

    public BinanceServerTimeProvider(
            RestAssistant restAssistant,
            String pathUrl,
            Exchange exchange,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.serverTime(exchange))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                pathUrl
        );
    }

    @Override
    public long getServerTimeMs() {
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(pathUrl)
                        .authRequired(false)
                        .build()
        );
        return body.get("serverTime").asLong();
    }
}
