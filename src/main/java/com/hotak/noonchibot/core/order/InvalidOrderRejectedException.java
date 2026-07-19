package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.web.ExchangeRejectedException;
import com.hotak.noonchibot.connector.web.ExchangeRestApiException;

public class InvalidOrderRejectedException extends ExchangeRejectedException {
    public InvalidOrderRejectedException(ExchangeRestApiException cause) {
        super(cause);
    }
}
