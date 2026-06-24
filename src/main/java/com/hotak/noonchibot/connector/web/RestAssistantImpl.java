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
        if(finalRequest.weightOverrides() == null) {
            return asyncThrottler.execute(
                    finalRequest.throttlerLimitId(),
                    () -> {
                        RestResponse response = call(finalRequest);
                        return applyPostProcessors(response);
                    }).join();
        }
        return asyncThrottler.execute(
                finalRequest.throttlerLimitId(),
                () -> {
                    RestResponse response = applyPostProcessors(call(finalRequest));
                    if (response.statusCode().isError() && request.throwError()) {
                        log.error("네트워크 문제 발생, response: {}", response);
                        throw new ExchangeApiException(response.statusCode(), response.body());
                    }
                    return response;
                },
                finalRequest.weightOverrides()).join();
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
                .headers(h-> {
                    if (request.headers() != null) h.addAll(request.headers());
                });
        if(request.body() != null) spec.body(request.body());
        ResponseEntity<String> entity = spec.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {}) // do noting.
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
