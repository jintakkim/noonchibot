package com.hotak.noonchibot.connector.web;

public interface WsPostProcessor {
    WsResponse process(WsResponse response);
}
