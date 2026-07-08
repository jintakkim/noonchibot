package com.hotak.noonchibot.connector;

public class InvalidOrderRejectedException extends ExchangeRejectedException {
    public InvalidOrderRejectedException(ExchangeApiException cause) {
        super(cause);
    }
}
