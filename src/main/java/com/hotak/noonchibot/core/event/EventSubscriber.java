package com.hotak.noonchibot.core.event;

@FunctionalInterface
public interface EventSubscriber {
    <E extends Event> Subscription subscribe(Class<E> eventType, EventHandler<E> listener, ExecutionPolicy policy);
}
