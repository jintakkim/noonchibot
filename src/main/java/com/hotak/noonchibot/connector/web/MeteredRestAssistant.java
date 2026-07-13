package com.hotak.noonchibot.connector.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

@RequiredArgsConstructor
public class MeteredRestAssistant implements RestAssistant {
    private static final String METRIC_NAME = "exchange.rest.requests";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final RestAssistant delegate;
    private final MeterRegistry meterRegistry;
    private final String exchange;

    @Override
    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            JsonNode body = delegate.executeRequestAndGetJsonBody(request);
            record(sample, request, SUCCESS);
            return body;
        } catch (RuntimeException e) {
            record(sample, request, FAILURE);
            throw e;
        }
    }

    @Override
    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            RestResponse response = delegate.executeRequestAndGetResponse(request);
            record(sample, request, SUCCESS);
            return response;
        } catch (RuntimeException e) {
            record(sample, request, FAILURE);
            throw e;
        }
    }

    private void record(
            Timer.Sample sample,
            RestRequest request,
            String result
    ) {
        sample.stop(
                Timer.builder(METRIC_NAME)
                        .description("Exchange REST request attempts")
                        .tag("exchange", exchange)
                        .tag("endpoint", endpointTag(request))
                        .tag("result", result)
                        .register(meterRegistry)
        );
    }


    private String endpointTag(RestRequest request) {
        return request.method().name().toLowerCase(Locale.ROOT)
                + ":" + request.pathUrl();
    }
}
