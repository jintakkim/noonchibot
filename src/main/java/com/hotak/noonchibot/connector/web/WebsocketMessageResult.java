package com.hotak.noonchibot.connector.web;

public record WebsocketMessageResult(Directive directive, String reason) {
    public enum Directive { ACKNOWLEDGED, IGNORED, MESSAGE_PROCESSED, RECONNECT }

    private static final WebsocketMessageResult ACKNOWLEDGED =
            new WebsocketMessageResult(Directive.ACKNOWLEDGED, null);
    private static final WebsocketMessageResult MESSAGE_PROCESSED =
            new WebsocketMessageResult(Directive.MESSAGE_PROCESSED, null);

    public static WebsocketMessageResult ignored(String reason) {
        return new WebsocketMessageResult(Directive.IGNORED, reason);
    }

    public static WebsocketMessageResult acknowledged() {
        return ACKNOWLEDGED;
    }

    public static WebsocketMessageResult processed() {
        return MESSAGE_PROCESSED;
    }

    public static WebsocketMessageResult reconnect(String reason) {
        return new WebsocketMessageResult(Directive.RECONNECT, reason);
    }

}
