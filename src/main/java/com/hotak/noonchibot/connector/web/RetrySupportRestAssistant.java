package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.core.utils.AsyncUtils;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.function.Supplier;

@RequiredArgsConstructor
class RetrySupportRestAssistant implements RestAssistant {
    private final RestAssistant delegate;
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
        return decorateRetry(() -> executeClassifying(request)).get();
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
