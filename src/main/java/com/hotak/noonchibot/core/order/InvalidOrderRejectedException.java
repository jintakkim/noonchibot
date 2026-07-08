package com.hotak.noonchibot.core.order;

public class InvalidOrderRejectedException extends ExchangeRejectedException {
    public InvalidOrderRejectedException(Throwable cause) {
        super(cause);
    }
}
