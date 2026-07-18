package com.hotak.noonchibot.connector;

import org.springframework.http.HttpStatusCode;

public class ExchangeAuthenticationException extends ExchangeApiException {
    public ExchangeAuthenticationException(HttpStatusCode httpStatusCode, String message) {
        super(httpStatusCode, message);
    }

    public ExchangeAuthenticationException(ExchangeApiException cause) {
        super(cause.httpStatusCode, cause.getMessage(), cause);
    }
}
