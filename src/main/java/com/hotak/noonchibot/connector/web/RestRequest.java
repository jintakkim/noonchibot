package com.hotak.noonchibot.connector.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.Map;

public record RestRequest(
        HttpMethod method,
        String pathUrl,
        Map<String, Object> params,
        Object body,
        HttpHeaders headers,
        boolean authRequired,
        String throttlerLimitId,
        // null 전달시 Property의 기본설정 weight 사용
        Map<String, Integer> weightOverrides
) {
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .method(method)
                .pathUrl(pathUrl)
                .params(params)
                .body(body)
                .headers(headers)
                .authRequired(authRequired)
                .throttlerLimitId(throttlerLimitId)
                .weightOverrides(weightOverrides);
    }

    public static class Builder {
        private HttpMethod method;
        private String pathUrl;
        private Map<String, Object> params;
        private Object body;
        private HttpHeaders headers = new HttpHeaders();
        private boolean authRequired = false;
        private String throttlerLimitId;
        private Map<String, Integer> weightOverrides = Collections.emptyMap();

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder pathUrl(String pathUrl) {
            this.pathUrl = pathUrl;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder authRequired(boolean authRequired) {
            this.authRequired = authRequired;
            return this;
        }

        public Builder throttlerLimitId(String throttlerLimitId) {
            this.throttlerLimitId = throttlerLimitId;
            return this;
        }

        public Builder weightOverrides(Map<String, Integer> weightOverrides) {
            this.weightOverrides = weightOverrides;
            return this;
        }

        public RestRequest build() {
            if (method == null) {
                throw new IllegalArgumentException("method is required");
            }
            if (pathUrl == null || pathUrl.isBlank()) {
                throw new IllegalArgumentException("pathUrl is required");
            }
            return new RestRequest(method, pathUrl, params, body, headers,
                    authRequired, throttlerLimitId, weightOverrides);
        }
    }
}
