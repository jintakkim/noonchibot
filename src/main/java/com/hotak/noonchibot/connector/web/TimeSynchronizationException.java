package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpStatusCode;

public class TimeSynchronizationException extends ExchangeRestApiException {
    public TimeSynchronizationException(HttpStatusCode statusCode, String responseBody) {
        super(statusCode, responseBody);
    }

    public TimeSynchronizationException(ExchangeRestApiException cause) {
        super(cause.statusCode(), cause.responseBody(), cause);
    }
}
