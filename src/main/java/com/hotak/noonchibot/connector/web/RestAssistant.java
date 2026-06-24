package com.hotak.noonchibot.connector.web;

import tools.jackson.databind.JsonNode;

public interface RestAssistant {
    JsonNode executeRequestAndGetJsonBody(RestRequest request);
    RestResponse executeRequestAndGetResponse(RestRequest request);
}
