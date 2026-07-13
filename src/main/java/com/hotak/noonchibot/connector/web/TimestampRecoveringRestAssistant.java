package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class TimestampRecoveringRestAssistant implements RestAssistant {
    private final RestAssistant delegate;
    private final TimeSynchronizer timeSynchronizer;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        return objectMapper.readTree(executeRequestAndGetResponse(request).body());
    }

    @Override
    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        try {
            return delegate.executeRequestAndGetResponse(request);
        } catch (ExchangeTimestampException e) {
            if (!request.authRequired()) {
                throw e;
            }
            return recoverAndRetry(request);
        }
    }

    private RestResponse recoverAndRetry(RestRequest request) {
        timeSynchronizer.updateServerTimeOffset();
        try {
            return delegate.executeRequestAndGetResponse(request);
        } catch (ExchangeTimestampException e) {
            throw new TimeSynchronizationException(e);
        }
    }
}
