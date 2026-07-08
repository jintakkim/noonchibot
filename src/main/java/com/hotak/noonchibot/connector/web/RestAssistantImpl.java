package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@RequiredArgsConstructor
public class RestAssistantImpl implements RestAssistant {
    private final RestClient restClient;
    private final List<RestPreProcessor> preProcessors;
    private final List<RestPostProcessor> postProcessors;
    private final Authenticator authenticator;
    private final AsyncThrottler asyncThrottler;
    private final ObjectMapper objectMapper;

    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        return objectMapper.readTree(executeRequestAndGetResponse(request).body());
    }

    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        RestRequest finalRequest = applyAuthentication(applyPreProcessors(request));
        if (finalRequest.weightOverrides() == null) {
            return join(asyncThrottler.execute(
                    finalRequest.throttlerLimitId(),
                    () -> {
                        RestResponse response = call(finalRequest);
                        return applyPostProcessors(response);
                    }));
        }
        return join(asyncThrottler.execute(
                finalRequest.throttlerLimitId(),
                () -> {
                    RestResponse response = applyPostProcessors(call(finalRequest));
                    if (response.statusCode().isError() && request.throwError()) {
                        log.error("rest request failed. response={}", response);
                        throw new ExchangeApiException(response.statusCode(), response.body());
                    }
                    return response;
                },
                finalRequest.weightOverrides()));
    }

    private <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
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
                    uriBuilder.path(request.pathUrl());
                    if (request.params() != null) {
                        request.params().forEach(uriBuilder::queryParam);
                    }
                    return uriBuilder.build();
                })
                .headers(headers -> {
                    if (request.headers() != null) {
                        headers.addAll(request.headers());
                    }
                });
        if (request.body() != null) {
            spec.body(request.body());
        }
        ResponseEntity<String> entity = spec.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                })
                .toEntity(String.class);
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
