package com.hotak.noonchibot.core.order;

public class ExchangeRejectedException extends RuntimeException {
    public ExchangeRejectedException(String message) {
        super(message);
    }

    public ExchangeRejectedException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
