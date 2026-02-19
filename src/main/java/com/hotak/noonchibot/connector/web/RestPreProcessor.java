package com.hotak.noonchibot.connector.web;

@FunctionalInterface
public interface RestPreProcessor {
    RestRequest process(RestRequest request);
}
