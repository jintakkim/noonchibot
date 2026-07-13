package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpStatusCode;

public class ExchangeTransientException extends ExchangeRestApiException {
    public ExchangeTransientException(HttpStatusCode statusCode, String responseBody) {
        super(statusCode, responseBody);
    }

    public ExchangeTransientException(ExchangeRestApiException cause) {
        super(cause.statusCode(), cause.responseBody(), cause);
    }
}
