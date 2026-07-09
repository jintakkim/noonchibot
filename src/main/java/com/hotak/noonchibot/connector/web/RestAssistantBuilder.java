package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.SimpleExchangeErrorClassifier;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.Objects;

public class RestAssistantBuilder {
    private static final ExchangeErrorClassifier DEFAULT_ERROR_CLASSIFIER = new SimpleExchangeErrorClassifier();
    private final RestAssistant delegate;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private String circuitName;
    private ExchangeErrorClassifier errorClassifier = DEFAULT_ERROR_CLASSIFIER;
    private int maxAttempts = 1;

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

    public RestAssistantBuilder maxAttempt(int maxAttempt) {
        if (maxAttempt < 0) {
            throw new IllegalArgumentException("maxAttempt must not be negative");
        }
        this.maxAttempts = maxAttempt;
        return this;
    }

    public RestAssistant build() {
        return new CircuitSupportRestAssistant(
                delegate,
                circuitBreakerRegistry.circuitBreaker(circuitName),
                errorClassifier,
                retry(errorClassifier)
        );
    }

    private Retry retry(ExchangeErrorClassifier classifier) {
        if (maxAttempts <= 1) {
            return null;
        }
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .retryOnException(classifier)
                .build();
        return Retry.of(retryName(), config);
    }

    private String retryName() {
        if (circuitName == null || circuitName.isBlank()) {
            return "rest-assistant-retry";
        }
        return circuitName + ".retry";
    }

}
