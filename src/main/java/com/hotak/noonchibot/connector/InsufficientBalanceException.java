package com.hotak.noonchibot.connector;

public class InsufficientBalanceException extends ExchangeRejectedException {
    public InsufficientBalanceException(ExchangeApiException cause) {
        super(cause);
    }
}
