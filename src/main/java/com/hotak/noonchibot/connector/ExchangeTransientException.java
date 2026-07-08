package com.hotak.noonchibot.connector;

import org.springframework.http.HttpStatusCode;

public class ExchangeTransientException extends ExchangeApiException {
    public ExchangeTransientException(HttpStatusCode httpStatusCode, String message) {
        super(httpStatusCode, message);
    }

    public ExchangeTransientException(ExchangeApiException cause) {
        super(cause.httpStatusCode, cause.getMessage(), cause);
    }
}
