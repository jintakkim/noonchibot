package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.SimpleExchangeErrorClassifier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.Objects;

public class RestAssistantBuilder {
    private static final String RETRY_NAME = "rest-assistant-retry";
    private static final ExchangeErrorClassifier DEFAULT_ERROR_CLASSIFIER = new SimpleExchangeErrorClassifier();
    private final RestAssistant delegate;
    private ExchangeErrorClassifier errorClassifier = DEFAULT_ERROR_CLASSIFIER;
    private int maxAttempts = 1;

    public RestAssistantBuilder(RestAssistant delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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
        return new RetrySupportRestAssistant(
                delegate,
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
        return Retry.of(RETRY_NAME, config);
    }

}
