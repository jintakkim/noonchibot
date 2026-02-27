package com.hotak.noonchibot.connector.web;

public record WsRequest(
    Object payload,
    boolean authRequired
) {}
