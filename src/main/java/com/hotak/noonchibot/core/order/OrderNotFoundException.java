package com.hotak.noonchibot.core.order;

public class OrderNotFoundException extends ExchangeRejectedException {
    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException(Throwable cause) {
        super(cause);
    }
}
