package com.hotak.noonchibot.connector.web;

/**
 * 이미 해당 상태이기 때문에 변경이 필요없을때 발생
 */
public class NoChangeRequiredException extends ExchangeRestApiException {
    public NoChangeRequiredException(ExchangeRestApiException cause) {
        super(cause.statusCode(), cause.responseBody());
    }
}
