package com.hotak.noonchibot.connector.web;

import lombok.Builder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Map;

@Builder(toBuilder = true)
public record RestRequest(
        HttpMethod method,
        String pathUrl,
        Map<String, Object> params,
        Object body,
        HttpHeaders headers,
        boolean authRequired,
        String throttlerLimitId,
        // null 전달시 Property의 기본설정 weight 사용
        Integer customWeight
) {
}