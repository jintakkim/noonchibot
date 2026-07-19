package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpStatusCode;

/**
 * 요청이 실행되지 않았다는게 보장이 될때
 */
public class RequestNotExecutedException extends ExchangeRestApiException {
    public RequestNotExecutedException(HttpStatusCode statusCode, String responseBody) {
        super(statusCode, responseBody);
    }

    public RequestNotExecutedException(ExchangeRestApiException cause) {
        super(cause.statusCode(), cause.responseBody(), cause);
    }
}
