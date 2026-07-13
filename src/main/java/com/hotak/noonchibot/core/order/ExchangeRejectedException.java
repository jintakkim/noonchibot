package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.web.ExchangeRestApiException;

public class ExchangeRejectedException extends ExchangeRestApiException {
    public ExchangeRejectedException(ExchangeRestApiException exception) {
        super(exception.statusCode(), exception.responseBody(), exception);
    }
}
