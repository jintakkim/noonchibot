package com.hotak.noonchibot.connector;

import org.springframework.http.HttpStatusCode;

public class ExchangeApiException extends RuntimeException {
    public final HttpStatusCode httpStatusCode;

    public ExchangeApiException(
            HttpStatusCode httpStatusCode,
            String message
    ) {
        super(message);
        this.httpStatusCode = httpStatusCode;
    }

    public ExchangeApiException(
            HttpStatusCode httpStatusCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
    }
}
