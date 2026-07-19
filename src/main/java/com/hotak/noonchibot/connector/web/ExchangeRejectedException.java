package com.hotak.noonchibot.connector.web;

public class ExchangeRejectedException extends RequestNotExecutedException {
    public ExchangeRejectedException(ExchangeRestApiException exception) {
        super(exception);
    }
}
