package com.hotak.noonchibot.connector.web;

@FunctionalInterface
public interface ServerTimeProvider {
    long getServerTimeMs();
}
