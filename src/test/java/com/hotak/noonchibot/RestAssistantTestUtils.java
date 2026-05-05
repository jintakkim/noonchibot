package com.hotak.noonchibot;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RestAssistantTestUtils {
    @SafeVarargs
    public static void stubByPath(RestAssistant restAssistant, Map.Entry<Predicate<RestRequest>, JsonNode>... routes) {
        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenAnswer(inv -> {
                    RestRequest req = inv.getArgument(0);
                    for (var route : routes) {
                        if (route.getKey().test(req)) return route.getValue();
                    }
                    throw new AssertionError("Unexpected request to: " + req.pathUrl());
                });
    }
}
