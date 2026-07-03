package com.hotak.noonchibot.client.websocket;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.INVALID_REQUEST;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.TYPE_REQUIRED;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.UNSUPPORTED_REQUEST;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.unsupportedRequest;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.TYPE_FIELD;

@Component
public class WebSocketRequestDispatcher {
    private final Map<RequestKey, WebSocketRequestHandler> handlers;

    public WebSocketRequestDispatcher(List<WebSocketRequestHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                handler -> new RequestKey(handler.method(), handler.subscriptionType()),
                Function.identity()
        ));
    }

    public void dispatch(String sessionId, String method, JsonNode subscription) {
        String subscriptionType = requiredSubscriptionType(subscription);
        WebSocketRequestHandler handler = handlers.get(new RequestKey(method, subscriptionType));
        if (handler == null) {
            throw new WebSocketRequestException(
                    UNSUPPORTED_REQUEST,
                    unsupportedRequest(method, subscriptionType)
            );
        }
        handler.handle(sessionId, subscription);
    }

    private String requiredSubscriptionType(JsonNode subscription) {
        if (subscription == null
                || subscription.get(TYPE_FIELD) == null
                || !subscription.get(TYPE_FIELD).isString()) {
            throw new WebSocketRequestException(INVALID_REQUEST, TYPE_REQUIRED);
        }
        return subscription.get(TYPE_FIELD).asString();
    }

    private record RequestKey(String method, String subscriptionType) {
    }
}
