package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.function.Function;

public class RestAssistantBuilder {
    private final RestAssistant delegate;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private String circuitName;
    private ExchangeErrorClassifier errorClassifier = ExchangeErrorClassifier.PASS_THROUGH;
    private Function<RestExchangeError, RestErrorAction> fourXxHandler = error -> RestErrorAction.DEFAULT;
    private int maxRetry;

    public RestAssistantBuilder(RestAssistant delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public RestAssistantBuilder circuit(CircuitBreakerRegistry circuitBreakerRegistry, String circuitName) {
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry");
        this.circuitName = Objects.requireNonNull(circuitName, "circuitName");
        return this;
    }

    public RestAssistantBuilder errorClassifier(ExchangeErrorClassifier errorClassifier) {
        this.errorClassifier = Objects.requireNonNull(errorClassifier, "errorClassifier");
        return this;
    }

    public RestAssistantBuilder on4xxError(Function<RestExchangeError, RestErrorAction> fourXxHandler) {
        this.fourXxHandler = Objects.requireNonNull(fourXxHandler, "fourXxHandler");
        return this;
    }

    public RestAssistantBuilder maxRetry(int maxRetry) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry must not be negative");
        }
        this.maxRetry = maxRetry;
        return this;
    }

    public RestAssistant build() {
        return new CircuitSupportRestAssistant(
                delegate,
                circuitBreakerRegistry == null || circuitName == null
                        ? null
                        : circuitBreakerRegistry.circuitBreaker(circuitName),
                errorClassifier,
                fourXxHandler,
                maxRetry,
                new ObjectMapper()
        );
    }

}
