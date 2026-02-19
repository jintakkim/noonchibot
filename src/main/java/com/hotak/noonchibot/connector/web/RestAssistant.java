package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RestAssistant {
    private final RestClient restClient;
    private final List<RestPreProcessor> preProcessors;
    private final List<RestPostProcessor> postProcessors;
    private final Authenticator authenticator;
    private final Throttler throttler;
    private final ObjectMapper objectMapper;

    public JsonNode executeRequestAndGetJsonBody(
            String url,
            String limitId,
            HttpMethod method,
            boolean authRequired,
            Map<String, Object> params,
            Map<String, Object> body,
            HttpHeaders headers
    ) {
        return objectMapper.readTree(executeRequestAndGetResponse(url, limitId, method, authRequired, params, body, headers).body());
    }

    public RestResponse executeRequestAndGetResponse(
            String url,
            String limitId,
            HttpMethod method,
            boolean authRequired,
            Map<String, Object> params,
            Map<String, Object> body,
            HttpHeaders headers
    ) {
        RestRequest request = RestRequest.builder()
                .url(url)
                .method(method)
                .authRequired(authRequired)
                .params(params)
                .body(body)
                .headers(headers)
                .throttlerLimitId(limitId)
                .build();

        request = applyPreProcessors(request);
        request = applyAuthentication(request);
        RestRequest finalRequest = request;
        return throttler.execute(request.throttlerLimitId(), () -> {
            RestResponse response = call(finalRequest);
            return applyPostProcessors(response);
        });
    }

    private RestRequest applyPreProcessors(RestRequest request) {
        RestRequest currentRequest = request;
        for (RestPreProcessor processor : preProcessors) {
            currentRequest = processor.process(currentRequest);
        }
        return currentRequest;
    }

    private RestRequest applyAuthentication(RestRequest request) {
        if (authenticator != null && request.authRequired()) {
            return authenticator.restAuthenticate(request);
        }
        return request;
    }

    private RestResponse call(RestRequest request) {
        RestClient.RequestBodySpec spec = restClient.method(request.method())
                .uri(uriBuilder -> {
                    uriBuilder.path(request.url());
                    if (request.params() != null) {
                        request.params().forEach(uriBuilder::queryParam);
                    }
                    return uriBuilder.build();
                })
                .headers(h-> {
                    if (request.headers() != null) h.addAll(request.headers());
                });
        if(request.body() != null) spec.body(request.body());
        ResponseEntity<String> entity = spec.retrieve().toEntity(String.class);
        return new RestResponse(entity.getStatusCode(), entity.getHeaders(), entity.getBody());
    }

    private RestResponse applyPostProcessors(RestResponse response) {
        RestResponse currentResponse = response;
        for (RestPostProcessor processor : postProcessors) {
            currentResponse = processor.process(currentResponse);
        }
        return currentResponse;
    }
}
