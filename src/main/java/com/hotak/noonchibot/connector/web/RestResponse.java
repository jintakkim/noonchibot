package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record RestResponse(
        HttpStatusCode statusCode,
        HttpHeaders headers,
        String body
) {
}
