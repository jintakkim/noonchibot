package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.core.utils.AsyncUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
class CircuitSupportRestAssistant implements RestAssistant {
    private final RestAssistant delegate;
    private final CircuitBreaker circuitBreaker;
    private final ExchangeErrorClassifier exchangeErrorClassifier;
    private final Retry retry;

    @Override
    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        return execute(() -> delegate.executeRequestAndGetJsonBody(request));
    }

    @Override
    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        return execute(() -> delegate.executeRequestAndGetResponse(request));
    }

    private <T> T execute(Supplier<T> request) {
        Supplier<T> retrying = decorateRetry(() -> executeClassifying(request));
        if (circuitBreaker == null) {
            log.warn("circuit breaker is null, running without circuitBreaker.");
            return retrying.get();
        }
        return CircuitBreaker.decorateSupplier(circuitBreaker, retrying).get();
    }

    private <T> Supplier<T> decorateRetry(Supplier<T> request) {
        if (retry == null) {
            return request;
        }
        return Retry.decorateSupplier(retry, request);
    }

    private <T> T executeClassifying(Supplier<T> request) {
        try {
            return request.get();
        } catch (RuntimeException e) {
            Throwable unwrapped = AsyncUtils.unwrapCompletionException(e);
            if (unwrapped instanceof ExchangeApiException exception) {
                throw exchangeErrorClassifier.classify(exception);
            }
            throw e;
        }
    }
}
