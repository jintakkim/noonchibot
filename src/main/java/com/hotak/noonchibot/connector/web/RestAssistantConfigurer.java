package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

public class RestAssistantConfigurer {
    private final RestAssistant delegate;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private String circuitName;
    private ExchangeErrorClassifier errorClassifier = ExchangeErrorClassifier.PASS_THROUGH;
    private Function<RestExchangeError, RestErrorAction> fourXxHandler = error -> RestErrorAction.DEFAULT;
    private int maxRetry;

    public RestAssistantConfigurer(RestAssistant delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public RestAssistantConfigurer circuit(CircuitBreakerRegistry circuitBreakerRegistry, String circuitName) {
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry");
        this.circuitName = Objects.requireNonNull(circuitName, "circuitName");
        return this;
    }

    public RestAssistantConfigurer errorClassifier(ExchangeErrorClassifier errorClassifier) {
        this.errorClassifier = Objects.requireNonNull(errorClassifier, "errorClassifier");
        return this;
    }

    public RestAssistantConfigurer on4xxError(Function<RestExchangeError, RestErrorAction> fourXxHandler) {
        this.fourXxHandler = Objects.requireNonNull(fourXxHandler, "fourXxHandler");
        return this;
    }

    public RestAssistantConfigurer maxRetry(int maxRetry) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry must not be negative");
        }
        this.maxRetry = maxRetry;
        return this;
    }

    public RestAssistant build() {
        return new ConfiguredRestAssistant(
                delegate,
                circuitBreakerRegistry,
                circuitName,
                errorClassifier,
                fourXxHandler,
                maxRetry
        );
    }

    private static class ConfiguredRestAssistant implements RestAssistant {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final RestAssistant delegate;
        private final CircuitBreaker circuitBreaker;
        private final ExchangeErrorClassifier errorClassifier;
        private final Function<RestExchangeError, RestErrorAction> fourXxHandler;
        private final int maxRetry;

        private ConfiguredRestAssistant(
                RestAssistant delegate,
                CircuitBreakerRegistry circuitBreakerRegistry,
                String circuitName,
                ExchangeErrorClassifier errorClassifier,
                Function<RestExchangeError, RestErrorAction> fourXxHandler,
                int maxRetry
        ) {
            this.delegate = delegate;
            this.circuitBreaker = circuitBreakerRegistry == null || circuitName == null
                    ? null
                    : circuitBreakerRegistry.circuitBreaker(circuitName);
            this.errorClassifier = errorClassifier;
            this.fourXxHandler = fourXxHandler;
            this.maxRetry = maxRetry;
        }

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
                JsonNode body = OBJECT_MAPPER.readTree(exception.getMessage());
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
}
