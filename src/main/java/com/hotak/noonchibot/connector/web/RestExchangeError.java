package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpStatusCode;

public record RestExchangeError(
        HttpStatusCode statusCode,
        Integer code,
        String body
) {
    public boolean is4xx() {
        return statusCode.is4xxClientError();
    }

    public boolean is5xx() {
        return statusCode.is5xxServerError();
    }
}
