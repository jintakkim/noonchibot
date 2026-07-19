package com.hotak.noonchibot.connector.web;

public class ExchangeRateLimitedException extends RequestNotExecutedException {
    public ExchangeRateLimitedException(ExchangeRestApiException cause) {
        super(cause);
    }
}
