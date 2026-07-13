package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpStatusCode;

public class ExchangeRestApiException extends RuntimeException {
    private final HttpStatusCode statusCode;
    private final String responseBody;

    public ExchangeRestApiException(HttpStatusCode statusCode, String responseBody) {
        super(responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public ExchangeRestApiException(
            HttpStatusCode statusCode,
            String responseBody,
            Throwable cause
    ) {
        super(responseBody, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
