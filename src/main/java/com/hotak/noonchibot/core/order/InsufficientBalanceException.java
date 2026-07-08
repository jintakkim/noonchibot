package com.hotak.noonchibot.core.order;

public class InsufficientBalanceException extends ExchangeRejectedException {
    public InsufficientBalanceException(Throwable cause) {
        super(cause);
    }
}
