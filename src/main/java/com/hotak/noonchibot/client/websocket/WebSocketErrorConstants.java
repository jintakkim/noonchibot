package com.hotak.noonchibot.client.websocket;

public final class WebSocketErrorConstants {
    public static final String ERROR_TYPE = "error";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String UNSUPPORTED_REQUEST = "UNSUPPORTED_REQUEST";
    public static final String INVALID_SUBSCRIPTION = "INVALID_SUBSCRIPTION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public static final String METHOD_REQUIRED = "websocket request method is required";
    public static final String TYPE_REQUIRED = "subscription type is required";
    public static final String PAIR_OR_PAIRS_REQUIRED = "pair or pairs is required";

    private WebSocketErrorConstants() {
    }

    public static String unsupportedRequest(String method, String subscriptionType) {
        return "unsupported websocket request: " + method + ' ' + subscriptionType;
    }
}
