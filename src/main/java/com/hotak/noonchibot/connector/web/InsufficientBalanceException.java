package com.hotak.noonchibot.connector.web;

public class InsufficientBalanceException extends ExchangeRejectedException {
    public InsufficientBalanceException(ExchangeRestApiException cause) {
        super(cause);
    }
}
