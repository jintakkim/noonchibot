package com.hotak.noonchibot.connector.web;

@FunctionalInterface
public interface RestPostProcessor {
    RestResponse process(RestResponse request);
}