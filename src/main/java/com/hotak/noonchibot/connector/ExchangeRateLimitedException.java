package com.hotak.noonchibot.connector;

public class ExchangeRateLimitedException extends ExchangeTransientException {
    public ExchangeRateLimitedException(ExchangeApiException cause) {
        super(cause);
    }
}
