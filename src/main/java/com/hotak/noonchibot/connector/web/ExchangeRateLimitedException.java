package com.hotak.noonchibot.connector.web;

public class ExchangeRateLimitedException extends ExchangeTransientException {
    public ExchangeRateLimitedException(ExchangeRestApiException cause) {
        super(cause);
    }
}
