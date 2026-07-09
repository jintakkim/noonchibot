package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
class CircuitSupportRestAssistant implements RestAssistant {
    private final RestAssistant delegate;
    private final CircuitBreaker circuitBreaker;
    private final ExchangeErrorClassifier errorClassifier;
    private final Function<RestExchangeError, RestErrorAction> fourXxHandler;
    private final int maxRetry;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        return execute(() -> delegate.executeRequestAndGetJsonBody(request));
    }

    @Override
    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        return execute(() -> delegate.executeRequestAndGetResponse(request));
    }

    private <T> T execute(Supplier<T> request) {
        Supplier<T> retrying = () -> executeWithRetry(request);
        if (circuitBreaker == null) {
            log.warn("circuit breaker is null, running without circuitBreaker.");
            return retrying.get();
        }
        return CircuitBreaker.decorateSupplier(circuitBreaker, retrying).get();
    }

    private <T> T executeWithRetry(Supplier<T> request) {
        int retryCount = 0;
        while (true) {
            try {
                return request.get();
            } catch (RuntimeException e) {
                Failure failure = classify(e);
                if (failure.retry() && retryCount < maxRetry) {
                    retryCount++;
                    continue;
                }
                throw failure.exception();
            }
        }
    }

    private Failure classify(RuntimeException exception) {
        Throwable unwrapped = exception instanceof CompletionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        if (unwrapped instanceof ExchangeApiException exchangeApiException) {
            RestExchangeError error = parse(exchangeApiException);
            RestErrorAction action = error.is4xx()
                    ? fourXxHandler.apply(error)
                    : RestErrorAction.DEFAULT;
            return switch (action) {
                case RETRY -> new Failure(new ExchangeTransientException(exchangeApiException), true);
                case IGNORE_AS_REJECTED -> new Failure(new ExchangeRejectedException(exchangeApiException), false);
                case RECORD_FAILURE -> new Failure(new ExchangeTransientException(exchangeApiException), false);
                case DEFAULT -> new Failure(errorClassifier.classify(exchangeApiException), false);
            };
        }
        if (unwrapped instanceof RuntimeException runtimeException) {
            return new Failure(runtimeException, false);
        }
        return new Failure(exception, false);
    }

    private RestExchangeError parse(ExchangeApiException exception) {
        Integer code = null;
        try {
            JsonNode body = objectMapper.readTree(exception.getMessage());
            if (body != null && body.has("code")) {
                code = body.get("code").asInt();
            }
        } catch (RuntimeException ignored) {
            // Keep the original body; not every exchange error is JSON.
        }
        return new RestExchangeError(exception.httpStatusCode, code, exception.getMessage());
    }

    private record Failure(RuntimeException exception, boolean retry) {
    }
}