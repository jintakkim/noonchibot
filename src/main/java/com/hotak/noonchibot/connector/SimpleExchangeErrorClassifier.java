package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class SimpleExchangeErrorClassifier implements ExchangeErrorClassifier {
    @Override
    public RuntimeException classify(ExchangeApiException exception) {
        if (exception instanceof ExchangeTransientException
                || exception instanceof ExchangeAuthenticationException) {
            return exception;
        }
        HttpStatusCode statusCode = exception.httpStatusCode;
        if (statusCode == HttpStatus.UNAUTHORIZED || statusCode == HttpStatus.FORBIDDEN) {
            return new ExchangeAuthenticationException(exception);
        }
        if (statusCode == HttpStatus.I_AM_A_TEAPOT || statusCode == HttpStatus.TOO_MANY_REQUESTS) {
            return new ExchangeRateLimitedException(exception);
        }
        if (statusCode.is4xxClientError()) {
            return new ExchangeRejectedException(exception);
        }
        return new ExchangeTransientException(exception);
    }

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof ExchangeTransientException;
    }
}
