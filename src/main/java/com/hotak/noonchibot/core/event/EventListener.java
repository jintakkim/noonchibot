package com.hotak.noonchibot.core.event;

@FunctionalInterface
public interface EventListener<T> {
    void call (T event);
}