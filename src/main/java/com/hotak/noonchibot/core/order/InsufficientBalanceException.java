package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.web.ExchangeRestApiException;

public class InsufficientBalanceException extends ExchangeRejectedException {
    public InsufficientBalanceException(ExchangeRestApiException cause) {
        super(cause);
    }
}
