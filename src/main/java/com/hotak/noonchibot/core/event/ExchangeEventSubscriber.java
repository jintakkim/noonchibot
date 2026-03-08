package com.hotak.noonchibot.core.event;

public interface ExchangeEventSubscriber {
    <T extends ExchangeEvent> void subscribe(Class<T> eventType, EventListener<T> listener);
    <T extends ExchangeEvent> void unsubscribe(Class<T> eventType, EventListener<T> listener);
}
