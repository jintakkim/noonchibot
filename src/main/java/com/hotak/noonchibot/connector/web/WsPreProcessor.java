package com.hotak.noonchibot.connector.web;

public interface WsPreProcessor {
    WsRequest process(WsRequest request);
}
