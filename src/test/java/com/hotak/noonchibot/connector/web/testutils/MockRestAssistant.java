package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.ExchangeRestApiException;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.RestResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class MockRestAssistant implements RestAssistant {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<RestFixture> fixtures = new ArrayList<>();
    private final List<RestRequest> recordedRequests = new ArrayList<>();
    private ExchangeErrorClassifier exchangeErrorClassifier;

    @Override
    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        RestFixture fixture = findMatchingFixture(request);
        return objectMapper.readTree(fixture.getThen().body());
    }

    @Override
    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        return findMatchingFixture(request).getThen();
    }

    private RestFixture findMatchingFixture(RestRequest request) {
        RestFixture match =  fixtures.stream()
                .filter(fixture -> fixture.isMatch(request))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No matching fixture found for request: " + request));

        if(!match.getThen().statusCode().is2xxSuccessful()) {
            ExchangeRestApiException exception = new ExchangeRestApiException(
                    match.getThen().statusCode(),
                    match.getThen().body()
            );
            throw exchangeErrorClassifier == null
                    ? exception
                    : exchangeErrorClassifier.classify(exception);
        }
        return match;
    }


    public void addFixture(RestFixture fixture) {
        fixtures.add(fixture);
    }

    public void setExchangeErrorClassifier(ExchangeErrorClassifier exchangeErrorClassifier) {
        this.exchangeErrorClassifier = exchangeErrorClassifier;
    }

    public void addFixture(List<RestFixture> fixtures) {
        this.fixtures.addAll(fixtures);
    }

    public void clearFixtures() {
        fixtures.clear();
    }

    public List<RestRequest> getRecordedRequests() {
        return List.copyOf(recordedRequests);
    }

    public RestRequest getLastRequest() {
        if (recordedRequests.isEmpty()) {
            throw new IllegalStateException("No requests recorded");
        }
        return recordedRequests.get(recordedRequests.size() - 1);
    }

    public int getRequestCount() {
        return recordedRequests.size();
    }

    public void clearRecordedRequests() {
        recordedRequests.clear();
    }

    public void reset() {
        fixtures.clear();
        recordedRequests.clear();
    }
}
