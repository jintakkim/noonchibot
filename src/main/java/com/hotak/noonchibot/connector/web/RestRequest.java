package com.hotak.noonchibot.connector.web;

import lombok.Builder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Map;

@Builder
public record RestRequest(
        HttpMethod method,
        String url,
        Map<String, Object> params,
        Object body,
        HttpHeaders headers,
        boolean authRequired,
        String throttlerLimitId
) {
}