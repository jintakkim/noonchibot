package com.hotak.noonchibot.connector.web;

@FunctionalInterface
public interface ServerTimeProvider {
    /**
     * blocking method(rest call)
     */
    long getServerTimeMs();
}
