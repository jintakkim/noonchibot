package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class DefaultExchangeErrorClassifier implements ExchangeErrorClassifier {
    @Override
    public RuntimeException classify(ExchangeApiException exception) {
        if (exception instanceof ExchangeTransientException) {
            return exception;
        }
        HttpStatusCode statusCode = exception.httpStatusCode;
        if (statusCode == HttpStatus.I_AM_A_TEAPOT || statusCode == HttpStatus.TOO_MANY_REQUESTS) {
            return new ExchangeRateLimitedException(exception);
        }
        if (statusCode.is4xxClientError()) {
            return new ExchangeRejectedException(exception);
        }
        return new ExchangeTransientException(exception);
    }
}
