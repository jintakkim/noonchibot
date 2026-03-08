package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.PubSub;

public class ExchangeEventBus implements ExchangeEventPublisher, ExchangeEventSubscriber {
    private final PubSub pubSub = new PubSub();

    @Override
    public <T extends ExchangeEvent> void publish(T event) {
        pubSub.triggerEvent(event);
    }

    @Override
    public <T extends ExchangeEvent> void subscribe(Class<T> eventType, EventListener<T> listener) {
        pubSub.addListener(eventType, listener);
    }

    @Override
    public <T extends ExchangeEvent> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        pubSub.removeListener(eventType, listener);
    }
}
