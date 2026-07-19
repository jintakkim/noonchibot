package com.hotak.noonchibot.connector.web;

public class ExchangeTimestampException extends RequestNotExecutedException {
    public ExchangeTimestampException(ExchangeRestApiException cause) {
        super(cause);
    }
}
