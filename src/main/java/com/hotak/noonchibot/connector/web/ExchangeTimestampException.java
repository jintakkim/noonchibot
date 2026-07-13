package com.hotak.noonchibot.connector.web;

public class ExchangeTimestampException extends ExchangeRestApiException {
    public ExchangeTimestampException(ExchangeRestApiException cause) {
        super(cause.statusCode(), cause.responseBody(), cause);
    }
}
