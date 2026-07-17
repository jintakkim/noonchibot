package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeProtocolException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class RestJsonBodyParser {
    private RestJsonBodyParser() {
    }

    public static JsonNode parse(
            ObjectMapper objectMapper,
            RestRequest request,
            RestResponse response
    ) {
        String source = request.method() + " " + request.pathUrl()
                + " (status " + response.statusCode().value() + ")";
        String body = response.body();

        if (body == null || body.isBlank()) {
            throw new ExchangeProtocolException(
                    "Expected JSON response body from " + source + " but body was empty",
                    null
            );
        }

        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException exception) {
            throw new ExchangeProtocolException(
                    "Invalid JSON response body from " + source,
                    exception
            );
        }
    }
}
