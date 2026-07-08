package com.hotak.noonchibot.connector;

import org.springframework.http.HttpStatusCode;

public class ExchangeRejectedException extends ExchangeApiException {
    public ExchangeRejectedException(HttpStatusCode httpStatusCode, String message) {
        super(httpStatusCode, message);
    }

    public ExchangeRejectedException(ExchangeApiException cause) {
        super(cause.httpStatusCode, cause.getMessage(), cause);
    }
}
