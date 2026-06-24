package com.hotak.noonchibot.core.event;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();

}
